package qp.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Transformation;

/**
 * Valida contra o catálogo as partes de uma QuerySpecification vindas do usuário.
 *
 * @author joao
 */
public final class QueryGuard {

    private static final Set<String> COMPARISON_OPERATORS = new LinkedHashSet<>(
            Arrays.asList("=", "!=", "<>", "<", "<=", ">", ">="));

    /** Colunas presentes em toda view de dataset. */
    private static final String ID_COLUMN = "id";

    private final Dataflow dataflow;

    private final Map<String, String> datasetTags = new LinkedHashMap<>();

    private final Map<String, Map<String, String>> datasetColumns = new LinkedHashMap<>();

    public QueryGuard(Dataflow dataflow) {
        if (dataflow == null) {
            throw new QueryValidationException(
                    "Dataflow nao encontrado: verifique a tag e a versao informadas.");
        }
        this.dataflow = dataflow;
        buildCatalog();
    }

    /** Valida e canoniza a especificação no lugar. */
    public static void enforce(QuerySpecification spec, Dataflow dataflow) {
        new QueryGuard(dataflow).validate(spec);
    }

    public void validate(QuerySpecification spec) {
        spec.setSources(canonicalizeDatasetTags(spec.getSources(), "source"));
        spec.setTargets(canonicalizeDatasetTags(spec.getTargets(), "target"));
        spec.setIncludes(canonicalizeDatasetTags(spec.getIncludes(), "include"));
        spec.setExcludes(canonicalizeDatasetTags(spec.getExcludes(), "exclude"));

        List<String> projections = new ArrayList<>();
        for (String projection : spec.getProjections()) {
            projections.add(canonicalizeProjection(projection));
        }
        spec.setProjections(projections);

        List<String> selections = new ArrayList<>();
        for (String selection : spec.getSelections()) {
            selections.add(canonicalizeSelection(selection));
        }
        spec.setSelections(selections);
    }

    // ---------------------------------------------------------------- catálogo

    private void buildCatalog() {
        for (Dataset dataset : dataflow.getDatasets()) {
            String tag = dataset.getTag();
            if (tag == null) {
                continue;
            }
            datasetTags.put(tag.toLowerCase(Locale.ROOT), tag);

            Map<String, String> columns = new LinkedHashMap<>();
            addColumn(columns, ID_COLUMN);

            if (dataset.getAttributes() != null) {
                for (Attribute attribute : dataset.getAttributes()) {
                    if (attribute.getName() != null) {
                        addColumn(columns, attribute.getName());
                    }
                }
            }

            // Uma coluna <transformacao>_task_id por transformação que produz ou consome o dataset.
            for (Transformation t : dataflow.getDatasetPreviousTransformations(dataset)) {
                addTaskIdColumn(columns, t);
            }
            for (Transformation t : dataflow.getDatasetNextTransformations(dataset)) {
                addTaskIdColumn(columns, t);
            }

            // Colunas dos extratores.
            for (Extractor extractor : dataset.getIndexingExtractors() == null
                    ? Collections.<Extractor>emptyList() : dataset.getIndexingExtractors()) {
                if (extractor.getTag() == null) {
                    continue;
                }
                String prefix = extractor.getTag().toLowerCase(Locale.ROOT);
                addColumn(columns, prefix + "_file_id");
                addColumn(columns, prefix + "_id");
                addColumn(columns, prefix + "_filename");
            }

            datasetColumns.put(tag, columns);
        }
    }

    private static void addColumn(Map<String, String> columns, String name) {
        columns.put(name.toLowerCase(Locale.ROOT), name);
    }

    private static void addTaskIdColumn(Map<String, String> columns, Transformation t) {
        if (t != null && t.getTag() != null) {
            addColumn(columns, t.getTag().toLowerCase(Locale.ROOT) + "_task_id");
        }
    }

    // ------------------------------------------------------------ tag de dataset

    private Collection<String> canonicalizeDatasetTags(Collection<String> tags, String clause) {
        List<String> canonical = new ArrayList<>();
        if (tags == null) {
            return canonical;
        }
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            canonical.add(resolveDataset(tag.trim(), clause));
        }
        return canonical;
    }

    private String resolveDataset(String tag, String clause) {
        String canonical = datasetTags.get(tag.toLowerCase(Locale.ROOT));
        if (canonical == null) {
            throw new QueryValidationException("Dataset desconhecido em " + clause + "(): '"
                    + tag + "'. Datasets disponiveis: " + describeDatasets() + ".");
        }
        return canonical;
    }

    private String describeDatasets() {
        return String.join(", ", datasetTags.values());
    }

    // ---------------------------------------------------------------- projeção

    private String canonicalizeProjection(String projection) {
        if (projection == null || projection.trim().isEmpty()) {
            throw new QueryValidationException("Projecao vazia em projection().");
        }
        Lexer lexer = new Lexer(projection, "projection()");
        String column = readColumn(lexer, "projection()");
        lexer.expectEnd("projection()");
        return column;
    }

    // ----------------------------------------------------------------- seleção

    private String canonicalizeSelection(String selection) {
        if (selection == null || selection.trim().isEmpty()) {
            throw new QueryValidationException("Condicao vazia em selection().");
        }
        Lexer lexer = new Lexer(selection, "selection()");
        StringBuilder sql = new StringBuilder(readColumn(lexer, "selection()"));

        Token token = lexer.next("selection()");
        boolean negated = false;
        if (token.isKeyword("NOT")) {
            negated = true;
            token = lexer.next("selection()");
        }

        if (!negated && token.type == Token.Type.OPERATOR
                && COMPARISON_OPERATORS.contains(token.text)) {
            sql.append(' ').append(token.text).append(' ').append(readOperand(lexer));
        } else if (token.isKeyword("LIKE")) {
            sql.append(negated ? " NOT LIKE " : " LIKE ").append(readStringLiteral(lexer, "LIKE"));
        } else if (token.isKeyword("IN")) {
            sql.append(negated ? " NOT IN (" : " IN (").append(readOperandList(lexer)).append(')');
        } else if (token.isKeyword("BETWEEN")) {
            String lower = readOperand(lexer);
            lexer.expectKeyword("AND", "BETWEEN");
            String upper = readOperand(lexer);
            sql.append(negated ? " NOT BETWEEN " : " BETWEEN ")
               .append(lower).append(" AND ").append(upper);
        } else if (!negated && token.isKeyword("IS")) {
            Token after = lexer.next("selection()");
            if (after.isKeyword("NOT")) {
                lexer.expectKeyword("NULL", "IS NOT");
                sql.append(" IS NOT NULL");
            } else if (after.isKeyword("NULL")) {
                sql.append(" IS NULL");
            } else {
                throw new QueryValidationException(
                        "Esperado NULL ou NOT NULL apos IS em selection(), encontrado '"
                        + after.text + "'.");
            }
        } else {
            throw new QueryValidationException("Operador nao permitido em selection(): '"
                    + token.text + "'. Use =, !=, <>, <, <=, >, >=, LIKE, IN, BETWEEN ou IS NULL.");
        }

        lexer.expectEnd("selection()");
        return sql.toString();
    }

    private String readOperandList(Lexer lexer) {
        lexer.expectOperator("(", "IN");
        StringBuilder operands = new StringBuilder(readOperand(lexer));
        while (lexer.peekOperator(",")) {
            lexer.next("IN");
            operands.append(", ").append(readOperand(lexer));
        }
        lexer.expectOperator(")", "IN");
        return operands.toString();
    }

    private String readOperand(Lexer lexer) {
        Token token = lexer.next("selection()");
        switch (token.type) {
            case NUMBER:
                return token.text;
            case STRING:
                return quote(token.text);
            case IDENTIFIER:
                return resolveColumn(token, lexer, "selection()");
            default:
                throw new QueryValidationException(
                        "Valor invalido em selection(): '" + token.text
                        + "'. Use um numero, um literal entre aspas simples ou dataset.atributo.");
        }
    }

    private String readStringLiteral(Lexer lexer, String context) {
        Token token = lexer.next(context);
        if (token.type != Token.Type.STRING) {
            throw new QueryValidationException(context + " exige um literal entre aspas simples,"
                    + " encontrado '" + token.text + "'.");
        }
        return quote(token.text);
    }

    private String readColumn(Lexer lexer, String context) {
        Token token = lexer.next(context);
        if (token.type != Token.Type.IDENTIFIER) {
            throw new QueryValidationException("Esperado dataset.atributo em " + context
                    + ", encontrado '" + token.text + "'.");
        }
        return resolveColumn(token, lexer, context);
    }

    /** Resolve <dataset>.<atributo> no catálogo e o reescreve com a grafia dele. */
    private String resolveColumn(Token datasetToken, Lexer lexer, String context) {
        lexer.expectOperator(".", context + ": as colunas devem ser qualificadas (dataset.atributo)");
        Token attributeToken = lexer.next(context);
        if (attributeToken.type != Token.Type.IDENTIFIER) {
            throw new QueryValidationException("Nome de atributo invalido em " + context
                    + ": '" + attributeToken.text + "'.");
        }

        String dataset = resolveDataset(datasetToken.text, context);
        Map<String, String> columns = datasetColumns.get(dataset);
        String column = columns.get(attributeToken.text.toLowerCase(Locale.ROOT));
        if (column == null) {
            throw new QueryValidationException("Atributo desconhecido em " + context + ": '"
                    + datasetToken.text + "." + attributeToken.text + "'. Atributos de "
                    + dataset + ": " + String.join(", ", columns.values()) + ".");
        }
        return dataset + "." + column;
    }

    /** Literal de texto do MonetDB, com aspas simples dobradas. */
    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    // --------------------------------------------------------------- analisador

    private static final class Token {

        enum Type { IDENTIFIER, NUMBER, STRING, OPERATOR, END }

        final Type type;
        final String text;

        Token(Type type, String text) {
            this.type = type;
            this.text = text;
        }

        boolean isKeyword(String keyword) {
            return type == Type.IDENTIFIER && text.equalsIgnoreCase(keyword);
        }
    }

    private static final class Lexer {

        private final String input;
        private final String context;
        private int position;
        private Token lookahead;

        Lexer(String input, String context) {
            this.input = input;
            this.context = context;
        }

        Token next(String where) {
            if (lookahead != null) {
                Token token = lookahead;
                lookahead = null;
                return token;
            }
            return read(where);
        }

        private Token peek() {
            if (lookahead == null) {
                lookahead = read(context);
            }
            return lookahead;
        }

        boolean peekOperator(String operator) {
            Token token = peek();
            return token.type == Token.Type.OPERATOR && token.text.equals(operator);
        }

        void expectOperator(String operator, String where) {
            Token token = next(where);
            if (token.type != Token.Type.OPERATOR || !token.text.equals(operator)) {
                throw new QueryValidationException("Esperado '" + operator + "' em " + where
                        + ", encontrado '" + token.text + "'.");
            }
        }

        void expectKeyword(String keyword, String where) {
            Token token = next(where);
            if (!token.isKeyword(keyword)) {
                throw new QueryValidationException("Esperado " + keyword + " em " + where
                        + ", encontrado '" + token.text + "'.");
            }
        }

        void expectEnd(String where) {
            Token token = next(where);
            if (token.type != Token.Type.END) {
                throw new QueryValidationException("Texto excedente em " + where + ": '"
                        + token.text + "'. Cada argumento aceita uma unica expressao;"
                        + " separe as demais com ';'.");
            }
        }

        private Token read(String where) {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
            if (position >= input.length()) {
                return new Token(Token.Type.END, "<fim>");
            }

            char current = input.charAt(position);

            if (current == '\'') {
                return readString(where);
            }
            if (Character.isDigit(current)
                    || (current == '-' && position + 1 < input.length()
                        && Character.isDigit(input.charAt(position + 1)))) {
                return readNumber();
            }
            if (current == '_' || Character.isLetter(current)) {
                return readIdentifier();
            }

            for (String operator : new String[] {"<>", "!=", "<=", ">=", "=", "<", ">", "(", ")", ",", "."}) {
                if (input.startsWith(operator, position)) {
                    position += operator.length();
                    return new Token(Token.Type.OPERATOR, operator);
                }
            }

            throw new QueryValidationException("Caractere nao permitido em " + where + ": '"
                    + current + "'. A expressao aceita apenas identificadores, numeros,"
                    + " literais entre aspas simples e operadores de comparacao.");
        }

        private Token readString(String where) {
            StringBuilder value = new StringBuilder();
            position++;
            while (position < input.length()) {
                char current = input.charAt(position);
                if (current == '\'') {
                    if (position + 1 < input.length() && input.charAt(position + 1) == '\'') {
                        value.append('\'');
                        position += 2;
                        continue;
                    }
                    position++;
                    return new Token(Token.Type.STRING, value.toString());
                }
                value.append(current);
                position++;
            }
            throw new QueryValidationException("Literal de texto sem aspa de fechamento em "
                    + where + ".");
        }

        private Token readNumber() {
            int start = position;
            if (input.charAt(position) == '-') {
                position++;
            }
            while (position < input.length() && Character.isDigit(input.charAt(position))) {
                position++;
            }
            if (position < input.length() && input.charAt(position) == '.'
                    && position + 1 < input.length() && Character.isDigit(input.charAt(position + 1))) {
                position++;
                while (position < input.length() && Character.isDigit(input.charAt(position))) {
                    position++;
                }
            }
            if (position < input.length()
                    && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                int mark = position;
                position++;
                if (position < input.length()
                        && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                if (position < input.length() && Character.isDigit(input.charAt(position))) {
                    while (position < input.length() && Character.isDigit(input.charAt(position))) {
                        position++;
                    }
                } else {
                    position = mark;
                }
            }
            return new Token(Token.Type.NUMBER, input.substring(start, position));
        }

        private Token readIdentifier() {
            int start = position;
            while (position < input.length()) {
                char current = input.charAt(position);
                if (current == '_' || Character.isLetterOrDigit(current)) {
                    position++;
                } else {
                    break;
                }
            }
            return new Token(Token.Type.IDENTIFIER, input.substring(start, position));
        }
    }
}

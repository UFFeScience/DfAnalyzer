package qp.query;

import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author vitor
 */
public class QuerySpecification {
    
    String dataflowTag = null;
    Integer dataflowVersion = null;
    //query specification
    AttrMapping.Type mapping = null;
    Collection<String> sources = null;
    Collection<String> targets = null;
    Collection<String> includes = null;
    Collection<String> excludes = null;
    Collection<String> selections = null;
    Collection<String> projections = null;

    public enum OperationType {
        MAPPING,
        SOURCE,
        TARGET,
        INCLUDE,
        EXCLUDE,
        SELECTION,
        PROJECTION
    }
    
    public QuerySpecification(String dataflowTag, Integer dataflowVersion,
            String message){
        this.dataflowTag = dataflowTag;
        this.dataflowVersion = dataflowVersion;
        this.sources = Arrays.asList();
        this.targets = Arrays.asList();
        this.includes = Arrays.asList();
        this.excludes = Arrays.asList();
        this.selections = Arrays.asList();
        this.projections = Arrays.asList();
        this.handleMessage(message);
    }

    /** Constrói a especificação sem passar pela linguagem de texto. */
    public QuerySpecification(String dataflowTag, Integer dataflowVersion,
            AttrMapping.Type mapping,
            Collection<String> sources, Collection<String> targets,
            Collection<String> includes, Collection<String> excludes,
            Collection<String> projections, Collection<String> selections) {
        this.dataflowTag = dataflowTag;
        this.dataflowVersion = dataflowVersion;
        this.mapping = mapping == null ? AttrMapping.Type.LOGICAL : mapping;
        this.sources = orEmpty(sources);
        this.targets = orEmpty(targets);
        this.includes = orEmpty(includes);
        this.excludes = orEmpty(excludes);
        this.projections = orEmpty(projections);
        this.selections = orEmpty(selections);
    }

    private static Collection<String> orEmpty(Collection<String> values) {
        return values == null ? Arrays.<String>asList() : values;
    }

    public String getDataflowTag() {
        return dataflowTag;
    }

    public Integer getDataflowVersion() {
        return dataflowVersion;
    }

    private void handleMessage(String message) {
        for(String functionStr : message.split("\n")){
            if (!functionStr.isEmpty()) {
                int startFunction = functionStr.indexOf("(");
                int endFunction = functionStr.lastIndexOf(")");

                if (startFunction > 0 && endFunction != -1) {
                    String functionName = functionStr.substring(0, startFunction)
                            .trim().toUpperCase();
                    OperationType function;
                    try {
                        function = OperationType.valueOf(functionName);
                    } catch (IllegalArgumentException ex) {
                        throw new QueryValidationException("Funcao de consulta desconhecida: '"
                                + functionName + "'. Use mapping, source, target, include,"
                                + " exclude, selection ou projection.");
                    }
                    String[] arguments = 
                            functionStr.substring(startFunction + 1, endFunction)
                                    .split(";");

                    for (int index = 0; index < arguments.length; index++) {
                        arguments[index] = arguments[index].trim();
                    }

                    switch(function){
                        case MAPPING:
                            try {
                                this.mapping = AttrMapping.Type.valueOf(
                                        arguments[0].trim().toUpperCase());
                            } catch (IllegalArgumentException ex) {
                                throw new QueryValidationException("Mapeamento desconhecido: '"
                                        + arguments[0] + "'. Use physical, logical ou hybrid.");
                            }
                            break;
                        case SOURCE:
                            this.sources = Arrays.asList(arguments);
                            break;
                        case TARGET:
                            this.targets = Arrays.asList(arguments);
                            break;
                        case INCLUDE:
                            this.includes = Arrays.asList(arguments);
                            break;
                        case EXCLUDE:
                            this.excludes = Arrays.asList(arguments);
                            break;
                        case PROJECTION:
                            this.projections = Arrays.asList(arguments);
                            break;
                        case SELECTION:
                            this.selections = Arrays.asList(arguments);
                            break;                        
                    }
                }
            }
        }
    }

    public AttrMapping.Type getMapping() {
        return mapping;
    }

    public Collection<String> getSources() {
        return sources;
    }

    public Collection<String> getTargets() {
        return targets;
    }

    public Collection<String> getIncludes() {
        return includes;
    }

    public Collection<String> getExcludes() {
        return excludes;
    }

    public Collection<String> getSelections() {
        return selections;
    }

    public Collection<String> getProjections() {
        return projections;
    }

    /* Setters usados pelo QueryGuard para trocar cada trecho do usuário pela forma canônica. */

    void setSources(Collection<String> sources) {
        this.sources = sources;
    }

    void setTargets(Collection<String> targets) {
        this.targets = targets;
    }

    void setIncludes(Collection<String> includes) {
        this.includes = includes;
    }

    void setExcludes(Collection<String> excludes) {
        this.excludes = excludes;
    }

    void setSelections(Collection<String> selections) {
        this.selections = selections;
    }

    void setProjections(Collection<String> projections) {
        this.projections = projections;
    }

}

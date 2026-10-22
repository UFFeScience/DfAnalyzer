package di.provenance;

import process.background.Provenance;
import di.enumeration.dataflow.AttributeType;
import di.enumeration.dataflow.DataflowType;
import di.enumeration.dbms.DBMS;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import di.dataflow.object.DataflowObject;
import di.object.dataflow.Attribute;
import di.object.dataflow.Dataflow;
import di.object.dataflow.Set;
import di.object.extraction.Extractor;
import di.object.process.Transaction;
import di.object.task.Element;
import di.object.task.Performance;
import di.object.task.Task;
import di.object.task.dfFile;
import di.main.Utils;

/**
 *
 * @author vitor
 */
public class TaskProvenance {

    public static Integer handleTaskTransaction(Connection db, Transaction t) {
        for (DataflowObject o : t.getObjects()) {
            if (o.getType() == DataflowType.TASK) {
                Task task = (Task) o;
                Dataflow df = Utils.handleDataflowFile(t.getPath() + Utils.DIR_SEPARATOR + task.dataflowTag, t.getDBMS());
                if (df == null) {
                    System.out.println("Error to store task, since dataflow "
                            + task.dataflowTag + " is not stored in database!");
                }
                if (Utils.verbose) {
                    Utils.print(0, "--------------------------------------------");
                    Utils.print(0, "Task - " + task.dataflowTag + " - " + task.transformationTag + " - " + task.ID + " - " + task.subID + " - " + task.status);
                }
//                if (df != null && task.transformationTag != null) {
                return storeTask(db, task, df, t.getDBMS());
//               } else {;
//                    Utils.print(0, "--------------------------------------------");
//                    System.err.println("Task - " + task.dataflowTag + " - " + task.ID + " - " + task.subID + " - " + task.status);
//                    return -1;
//                }
            }
        }
        return null;
    }

    public static Integer handleTaskTransaction(Connection db, Transaction t, Dataflow df) {
        for (DataflowObject o : t.getObjects()) {
            if (o.getType() == DataflowType.TASK) {
                Task task = (Task) o;

                if (Utils.verbose) {
                    Utils.print(0, "Task - " + task.dataflowTag + " - " + task.transformationTag + " - " + task.ID + " - " + task.subID + " - " + task.status);
                }
                if (df != null && task.transformationTag != null) {
                    return storeTask(db, task, df, t.getDBMS());
                } else {
                    throw new IllegalStateException("task descartada: dataflow '"
                            + task.dataflowTag + "' nao pode ser resolvido"
                            + (task.transformationTag == null
                                ? " e a transformacao nao foi informada" : "")
                            + " (task " + task.ID + ", status " + task.status + ")");
                }
            }
        }
        return null;
    }

    private static Integer storeTask(Connection db, Task t, Dataflow df, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                // MemSQL não grava a execução da task.
                rs = insertTask(db, t.ID, t.dataflowTag, t.transformationTag, t.status, t.workspace, t.resource, t.output, t.error);
            } else {
                rs = st.executeQuery(
                    "SELECT insertTask(" +
                    "CAST(" + t.ID + " AS INTEGER)," +
                    "CAST('" + sqlLiteral(t.dataflowTag) + "' AS VARCHAR(50))," +
                    "CAST('" + sqlLiteral(t.transformationTag) + "' AS VARCHAR(50))," +
                    "CAST('" + sqlLiteral(t.status) + "' AS VARCHAR(10))," +
                    "CAST('" + sqlLiteral(t.workspace) + "' AS VARCHAR(500))," +
                    "CAST('" + sqlLiteral(t.resource) + "' AS VARCHAR(100))," +
                    "CAST('" + sqlLiteral(t.output) + "' AS CLOB)," +
                    "CAST('" + sqlLiteral(t.error) + "' AS CLOB)," +
                    "CAST('" + sqlLiteral(t.executionTag) + "' AS VARCHAR(100))" +
                    ");"
                );

            }
            if (rs.next()) {
                t.ID = rs.getInt(1);
            }

            resolveDependencyTasks(db, t);

            ArrayList<Set> taskSets = df.getSetsFromTransformation(t.transformationTag);
            for (Set s : taskSets) {
                t.updateFiles(s);
            }

            if (!t.files.isEmpty()) {
                storeFiles(db, t, dbms);
            }

            if (!t.performances.isEmpty()) {
                storePerformances(db, t, dbms);
            }

            if (!t.elements.isEmpty()) {
                storeElements(db, t, df, dbms);
            }
        } catch (NumberFormatException | SQLException ex) {
            ex.printStackTrace();
            String msg = "[Error] Insert task from transformation "
                    + t.transformationTag + " with identifier " + t.ID;
            if (t.subID == null) {
                msg += " and subidentifier " + t.subID;
            }
            Utils.print(0, msg);
            return null;
        }

        return t.ID;
    }

    /** Procura a produtora no mesmo dataflow, versão e execução da consumidora. */
    static final String DEPENDENCY_TASK_SQL =
            "SELECT p.id FROM task p, data_transformation dt, task c "
            + "WHERE c.id = ? AND p.dt_id = dt.id AND dt.tag = ? AND p.identifier = ? "
            + "AND p.df_version = c.df_version "
            + "AND (p.df_exec = c.df_exec OR (p.df_exec IS NULL AND c.df_exec IS NULL)) "
            + "ORDER BY p.id DESC";

    private static void resolveDependencyTasks(Connection db, Task t) throws SQLException {
        for (String[] record : t.dependencyIDs) {
            if (record.length != t.dependencyTags.size()) {
                Utils.print(0, "[Warning] Task " + t.transformationTag + ": dependencia com "
                        + record.length + " id(s) para " + t.dependencyTags.size()
                        + " tag(s) - registro ignorado, nenhuma ligacao gravada por ele.");
                continue;
            }
            for (int index = 0; index < t.dependencyTags.size(); index++) {
                String identifier = record[index].trim();
                if (!Utils.isNumericArray(identifier)) {
                    continue;
                }
                String depTag = t.dependencyTags.get(index).toLowerCase();
                Integer producer = findDependencyTask(db, t.ID, depTag, Integer.parseInt(identifier));
                if (producer == null) {
                    Utils.print(0, "[Warning] Task " + t.transformationTag + ": task produtora '"
                            + depTag + "' com identificador " + identifier
                            + " nao encontrada nesta execucao - ligacao nao gravada.");
                    continue;
                }
                HashMap<String, Integer> hmap = new HashMap<>();
                hmap.put(depTag, producer);
                t.dependencyTaskIDs.add(hmap);
            }
        }
    }

    private static Integer findDependencyTask(Connection db, Integer consumerTaskId,
            String depTag, Integer identifier) throws SQLException {
        try (PreparedStatement st = db.prepareStatement(DEPENDENCY_TASK_SQL)) {
            st.setInt(1, consumerTaskId);
            st.setString(2, depTag);
            st.setInt(3, identifier);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static void storeFiles(Connection db, Task t, DBMS dbms) {
        try {
            Statement st = db.createStatement();

            StringBuilder sb = null;
            ResultSet rs = null;
            for (dfFile f : t.files) {
                if (Utils.verbose) {
                    Utils.print(1, "File - " + f.path + " - " + f.name);
                }

                if (dbms.equals(DBMS.MEMSQL)) {
                    rs = insertFile(db, t.ID, f.name, f.path);
                } else {
                    sb = new StringBuilder();
                    sb.append("SELECT insertFile(").append(t.ID).append(",'").append(f.name).append("','").append(f.path).append("');").append("\n");
                    rs = st.executeQuery(sb.toString());
                }
                if (rs.next()) {
                    f.ID = rs.getInt(1);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void storePerformances(Connection db, Task t, DBMS dbms) {
        try {
            Statement st = db.createStatement();

            StringBuilder sb = null;
            ResultSet rs = null;

            for (Performance p : t.performances) {

                if (dbms.equals(DBMS.MEMSQL)) {
                    rs = insertPerformance(db, t.ID, t.subID, p.method, p.description, p.startTime, p.endTime, p.invocation);
                } else {
                    sb = new StringBuilder();

                    // Texto vindo do cliente.
                    sb.append("SELECT insertPerformance(").append(t.ID).append(",").append(t.subID).append(",'")
                            .append(sqlLiteral(p.method)).append("','").append(sqlLiteral(p.description)).append("','")
                            .append(sqlLiteral(p.startTime)).append("','").append(sqlLiteral(p.endTime)).append("','")
                            .append(sqlLiteral(String.valueOf(p.invocation))).append("');").append("\n");

                    rs = st.executeQuery(sb.toString());
                }

                if (rs.next()) {
                    p.ID = rs.getInt(1);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void storeElements(Connection db, Task t, Dataflow df, DBMS dbms) throws SQLException {
        Iterable<Set> sets = df.getSetsFromTransformation(t.transformationTag);
        for (Set s : sets) {
            if (Utils.verbose) {
                Utils.print(1, "Elements to Data Set - " + s.tag);
            }
            insertElements(db, t, s, dbms);
        }
    }

    private static void insertElements(Connection db, Task t, Set s, DBMS dbms) throws SQLException {
        if (s.dependencyTransformation == null) {
            insertElementsWithoutDependency(db, t, s, dbms);
        } else {
            updateElementsWithDependency(db, t, s, dbms);
        }
    }

    /** Linhas por executeBatch. */
    private static final int INSERT_BATCH_SIZE = 500;

    /** Grava os elementos de um set como linhas da tabela `ds_<tag>`. */
    private static void insertElementsWithoutDependency(Connection db, Task t, Set s, DBMS dbms) {
        ArrayList<Attribute> tableColumns = new ArrayList<>();
        ArrayList<String> columnNames = new ArrayList<>();
        ArrayList<String> exts = new ArrayList<>();
        for (Attribute a : s.getAttributes()) {
            if (a.extractorTag == null) {
                columnNames.add(a.name.toLowerCase());
                tableColumns.add(a);
            } else {
                String fileExtCol = Utils.getExtractorFileColumn(a.extractorTag);
                if (!exts.contains(fileExtCol)) {
                    columnNames.add(fileExtCol);
                    Attribute extractorAtt = new Attribute();
                    extractorAtt.name = fileExtCol;
                    extractorAtt.extractorTag = a.extractorTag;
                    extractorAtt.type = AttributeType.EXTRACTION_TYPE;
                    tableColumns.add(extractorAtt);
                    exts.add(fileExtCol);
                }
            }
        }

        String tableName = Utils.getDataSetTableName(s.tag.toLowerCase());
        String sql = buildInsertSQL(tableName, t.transformationTag.toLowerCase() + "_task_id", columnNames, dbms);

        ArrayList<Object[]> rows = getElementRows(db, t, s, tableColumns, dbms);
        if (rows.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = db.prepareStatement(sql)) {
            int pending = 0;
            for (Object[] row : rows) {
                // Posição 1 é o <transformacao>_task_id; as demais seguem tableColumns.
                stmt.setInt(1, (Integer) row[0]);
                for (int i = 0; i < tableColumns.size(); i++) {
                    bindValue(stmt, i + 2, tableColumns.get(i).type, row[i + 1]);
                }
                stmt.addBatch();
                if (++pending == INSERT_BATCH_SIZE) {
                    stmt.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                stmt.executeBatch();
            }
        } catch (SQLException ex) {
            Logger.getLogger(Provenance.class.getName()).log(Level.SEVERE,
                    "falha ao gravar elementos em " + tableName + " (task " + t.ID + ")", ex);
        }
    }

    /** Só identificadores no texto do SQL; os valores vão como parâmetros. */
    static String buildInsertSQL(String tableName, String taskColumn, List<String> columns, DBMS dbms) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        if (dbms.equals(DBMS.MEMSQL)) {
            sql.append(tableName);
        } else {
            sql.append('"').append(tableName).append('"');
        }
        sql.append('(').append(taskColumn);
        for (String c : columns) {
            sql.append(',').append(c);
        }
        sql.append(") VALUES (?");
        for (int i = 0; i < columns.size(); i++) {
            sql.append(",?");
        }
        return sql.append(')').toString();
    }

    /** Tamanho das colunas criadas em DataflowProvenance.getAttributeStatement. */
    static final int TEXT_MAX_LENGTH = 500;
    static final int FILE_MAX_LENGTH = 300;

    /** Converte o texto do cliente para o tipo da coluna. */
    static Object toJdbcValue(AttributeType type, String raw) {
        if (raw != null && type == AttributeType.TEXT && raw.length() > TEXT_MAX_LENGTH) {
            return null;
        }
        if (raw != null && type == AttributeType.FILE && raw.length() > FILE_MAX_LENGTH) {
            return null;
        }
        if (type == AttributeType.NUMERIC) {
            if (raw == null) {
                return null;
            }
            try {
                double d = Double.parseDouble(raw.trim());
                return (Double.isNaN(d) || Double.isInfinite(d)) ? null : d;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return raw;
    }

    private static void bindValue(PreparedStatement stmt, int index, AttributeType type, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, sqlTypeFor(type));
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else {
            stmt.setString(index, value.toString());
        }
    }

    private static int sqlTypeFor(AttributeType type) {
        if (type == AttributeType.NUMERIC) {
            return Types.DOUBLE;
        }
        if (type == AttributeType.EXTRACTION_TYPE) {
            return Types.INTEGER;
        }
        return Types.VARCHAR;
    }

    private static void updateElementsWithDependency(Connection db, Task t, Set s, DBMS dbms) throws SQLException {
        try {
            if (!t.dependencyTaskIDs.isEmpty()) {

                StringBuilder query = new StringBuilder();
                //query.append("START TRANSACTION;" + Utils.NEW_LINE);

                if (dbms.equals(DBMS.MEMSQL)) {
                    query.append("UPDATE ")
                            .append(Utils.getDataSetTableName(s.tag.toLowerCase()))
                            .append(Utils.NEW_LINE).append(" SET ")
                            .append(t.transformationTag.toLowerCase()).append("_task_id")
                            .append("=").append(t.ID)
                            .append(Utils.NEW_LINE).append(" WHERE ");
                } else {
                    query.append("UPDATE \"")
                            .append(Utils.getDataSetTableName(s.tag.toLowerCase()))
                            .append("\"").append(Utils.NEW_LINE).append(" SET ")
                            .append(t.transformationTag.toLowerCase()).append("_task_id")
                            .append("=").append(t.ID)
                            .append(Utils.NEW_LINE);
                }

                boolean hasCondition = false;
                for (HashMap<String, Integer> hmap : t.dependencyTaskIDs) {
                    boolean firstCol = true;

                    for (Map.Entry<String, Integer> entry : hmap.entrySet()) {
                        if (entry.getKey().equals(s.dependencyTransformation.tag)) {
                            if (!hasCondition) {
                                query.append(" WHERE (");
                                hasCondition = true;
                            } else {
                                query.append(" OR (");
                            }

                            if (firstCol) {
                                firstCol = false;
                            }

                            query.append(entry.getKey().toLowerCase()).append("_task_id")
                                    .append("=").append(entry.getValue())
                                    .append(" AND ")
                                    .append(t.transformationTag.toLowerCase()).append("_task_id")
                                    .append(" IS NULL");

                            query.append(")");
                        }
                    }

                    query.append(Utils.NEW_LINE);
                }

                //query.append(";").append(Utils.NEW_LINE).append(" COMMIT;");
                query.append(";");
//                if (Utils.verbose) {
//                    System.out.println(query.toString());
//                }

                if (hasCondition) {
                    PreparedStatement st = db.prepareStatement(query.toString());
                    st.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(Provenance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static ArrayList<Object[]> getElementRows(Connection db, Task task,
            Set set, ArrayList<Attribute> attributes, DBMS dbms) {
        ArrayList<Object[]> result = new ArrayList<>();
        for (Element e : task.elements) {
            if (e.setTag.equals(set.tag)) {
                Object[] row = getElementRow(db, set, e, attributes, task, dbms);
                if (row != null) {
                    result.add(row);
                }
            }
        }
        return result;
    }

    /** Uma linha: [task.ID, valor1, valor2, ...] alinhada a `attributes`. */
    private static Object[] getElementRow(Connection db, Set set, Element element,
            ArrayList<Attribute> attributes, Task task, DBMS dbms) {
        if (element.values.size() != attributes.size()) {
            Logger.getLogger(Provenance.class.getName()).log(Level.WARNING,
                    "elemento do set {0} (task {1}) descartado: {2} valores para {3} atributos",
                    new Object[]{set.tag, task.ID, element.values.size(), attributes.size()});
            return null;
        }

        Object[] row = new Object[attributes.size() + 1];
        row[0] = task.ID;
        for (int i = 0; i < attributes.size(); i++) {
            Attribute a = attributes.get(i);
            String currentValue = element.values.get(i);

            if (a.type == AttributeType.EXTRACTION_TYPE) {
                Integer fileID = null;
                if (!set.extractors.isEmpty()) {
                    String filePath = currentValue;
                    fileID = Utils.getFileID(task.files, filePath);
                    if (!set.propagatedExtractors.containsKey(a.extractorTag)) {
                        storeDataIntoExtractorTable(db, set, a.extractorTag, filePath, fileID, dbms);
                    }
                }
                row[i + 1] = fileID;
            } else {
                Object value = toJdbcValue(a.type, currentValue);
                if (value == null && currentValue != null) {
                    String motivo = a.type == AttributeType.NUMERIC
                            ? "valor NUMERIC nao numerico: \"" + currentValue + "\""
                            : "valor " + a.type + " com " + currentValue.length()
                                    + " caracteres, maior que a coluna";
                    Logger.getLogger(Provenance.class.getName()).log(Level.WARNING,
                            "{0}.{1} (task {2}): {3} - gravado como NULL",
                            new Object[]{set.tag, a.name, task.ID, motivo});
                }
                row[i + 1] = value;
            }
        }
        return row;
    }

    private static void storeDataIntoExtractorTable(Connection db, Set set,
            String extractorTag, String filePath, int fileID, DBMS dbms) {
        try {
            Extractor extractor = set.getExtractor(extractorTag);

            //Statement st = db.createStatement();
            //String sqlFooter = ";" + Utils.NEW_LINE + "COMMIT;";
            String sqlFooter = ";";

            StringBuilder sqlHeader = new StringBuilder();

            if (dbms.equals(DBMS.MEMSQL)) {
                //sqlHeader.append("START TRANSACTION;" + Utils.NEW_LINE)
                sqlHeader.append("INSERT INTO ")
                        .append(Utils.getExtractorTableName(extractor.tag))
                        .append("(")
                        .append(Utils.getExtractorFileColumn(extractorTag));
            } else {
                //sqlHeader.append("START TRANSACTION;" + Utils.NEW_LINE)

                sqlHeader.append("INSERT INTO \"")
                        .append(Utils.getExtractorTableName(extractor.tag))
                        .append("\"(")
                        .append(Utils.getExtractorFileColumn(extractorTag));
            }

            FileReader fr = new FileReader(filePath);
            BufferedReader br = new BufferedReader(fr);
            if (br.ready()) {
                String line = br.readLine().replaceAll(";", ",")
                        .replaceAll("\"", "")
                        .replaceAll(":", "")
                        .toLowerCase();
                sqlHeader.append(",").append(line);
            }

            // to work with apache spark (rdd file without header)
//            for (Attribute att : set.attributes) {
//                sqlHeader.append(",").append(att.name);
//            }
            sqlHeader.append(") VALUES ");

//            capacity in megabytes
            int capacity = 10000;
            StringBuilder sqlValues = new StringBuilder();
            int dataElements = 0;
            boolean hasPreviousRecord = false;
            while (br.ready()) {
                if (hasPreviousRecord) {
                    sqlValues.append(",\n");
                } else {
                    hasPreviousRecord = true;
                }
                sqlValues.append("(")
                        .append(fileID)
                        .append(",")
                        .append(br.readLine().replaceAll("\"", "'").replaceAll(";", ","))
                        .append(")");
                dataElements++;

                if (sqlValues.length() > capacity) {
                    StringBuilder query = new StringBuilder();
                    query.append(sqlHeader).append(sqlValues).append(sqlFooter);
                    if (Utils.debug) {
                        System.out.println(query.toString());
                    } else if (Utils.verbose) {
                        Utils.print(2, "Inserting " + dataElements + " data elements on database...");
                    }
                    dataElements = 0;
                    PreparedStatement stmt = db.prepareStatement(query.toString());
                    stmt.executeUpdate();
                    sqlValues = new StringBuilder();
                    hasPreviousRecord = false;
                }
            }

            if (sqlValues.length() > 0) {
                StringBuilder query = new StringBuilder();
                query.append(sqlHeader).append(sqlValues).append(sqlFooter);
                if (Utils.debug) {
                    System.out.println(query.toString());
                }
                if (Utils.verbose) {
                    Utils.print(2, "Inserting " + dataElements + " data elements on database...");
                }
                PreparedStatement stmt = db.prepareStatement(query.toString());
                stmt.executeUpdate();
                //st.execute(query.toString());
            }

            br.close();
            fr.close();
        } catch (SQLException | IOException ex) {
            Logger.getLogger(Provenance.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Dobra aspas simples para uso como literal SQL. */
    private static String sqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static ResultSet insertTask(Connection db, Integer videntifier, String vdf_tag, String vdt_tag, String vstatus, String vworkspace, String vcomputing_resource, String voutput_msg, String verror_msg) throws SQLException {
        Integer vid = null, vdf_version = null, vdt_id = null;
        String vvstatus;

        PreparedStatement st = db.prepareStatement("SELECT dfv.version, dt.id FROM dataflow df, data_transformation dt, dataflow_version as dfv "
                + "WHERE df.id = dt.df_id AND dfv.df_id = df.id AND df.tag = (?) AND dt.tag = (?);");
        st.setString(1, vdf_tag);
        st.setString(2, vdt_tag);
        ResultSet rs = st.executeQuery();

        if (rs.next()) {
            vdf_version = rs.getInt(1);
            vdt_id = rs.getInt(2);
        }

        if (vdf_version != null && vdt_id != null) {
            st = db.prepareStatement("SELECT t.id, t.status FROM task t WHERE t.df_version = (?) AND t.dt_id = (?) AND t.identifier = (?);");
            st.setInt(1, vdf_version);
            st.setInt(2, vdt_id);
            st.setInt(3, videntifier);
            rs = st.executeQuery();

            if (rs.next()) {
                vid = rs.getInt(1);
                vvstatus = rs.getString(2);
            }

            if (vid == null) {
                st = db.prepareStatement("INSERT INTO task(identifier,df_version,dt_id,status,workspace,computing_resource,output_msg,error_msg) VALUES(?,?,?,?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
                st.setInt(1, videntifier);
                st.setInt(2, vdf_version);
                st.setInt(3, vdt_id);
                st.setString(4, vstatus);
                st.setString(5, vworkspace);
                st.setString(6, vcomputing_resource);
                st.setString(7, voutput_msg);
                st.setString(8, verror_msg);
                st.executeUpdate();
                rs = st.getGeneratedKeys();
            } else {
                st = db.prepareStatement("UPDATE task SET status=(?), output_msg=(?), error_msg=(?) WHERE identifier=(?) AND df_version=(?) AND dt_id=(?);");
                st.setString(1, vstatus);
                st.setString(2, voutput_msg);
                st.setString(3, verror_msg);
                st.setInt(4, videntifier);
                st.setInt(5, vdf_version);
                st.setInt(6, vdt_id);
                st.executeUpdate();
            }
        }

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertFile(Connection db, Integer vtask_id, String vname, String vpath) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM file WHERE name = (?) AND path = (?);");
        st.setString(1, vname);
        st.setString(2, vpath);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO file(task_id,name,path) VALUES(?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, vtask_id);
            st.setString(2, vname);
            st.setString(3, vpath);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertPerformance(Connection db, Integer vtask_id, Integer vsubtask_id, String vmethod, String vdescription, String vstarttime, String vendtime, String vinvocation) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM performance WHERE method = (?);");
        st.setString(1, vmethod);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO performance(task_id,subtask_id,method,description,starttime,endtime,invocation) VALUES(?,?,?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, vtask_id);
            st.setInt(2, vsubtask_id);
            st.setString(3, vmethod);
            st.setString(4, vdescription);
            st.setString(5, vstarttime);
            st.setString(6, vendtime);
            st.setString(7, vinvocation);

            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }

}

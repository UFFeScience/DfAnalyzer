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
import java.util.ArrayList;
import java.util.HashMap;
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
                    System.err.println(" ERROR: Task - " + task.dataflowTag + " - " + task.ID + " - " + task.subID + " - " + task.status);
                    return -1;
                }
            }
        }
        return null;
    }

    private static Integer storeTask(Connection db, Task t, Dataflow df, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            if (!t.dependencyIDs.isEmpty()) {
                for (int i = 0; i < t.dependencyIDs.size(); i++) {
                    String[] record = t.dependencyIDs.get(i);
                    if (record.length == t.dependencyTags.size()) {
                        for (int index = 0; index < t.dependencyTags.size(); index++) {
                            if (Utils.isNumericArray(record[index])) {
                                HashMap<String, Integer> hmap = new HashMap<>();
                                hmap.put(t.dependencyTags.get(index).toLowerCase(),
                                        DataflowProvenance.getTransformationID(db, t.dataflowTag, t.dependencyTags.get(index).toLowerCase(), Integer.parseInt(record[index])));
                                t.dependencyTaskIDs.add(hmap);
                            }
                        }
                    }
                }
            }

            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertTask(db, t.ID, t.dataflowTag, t.transformationTag, t.status, t.workspace, t.resource, t.output, t.error);
            } else {
                rs = st.executeQuery("SELECT insertTask(" + t.ID + ",'"
                        + t.dataflowTag + "','" + t.transformationTag + "','"
                        + t.status + "','" + t.workspace + "','" + t.resource + "','"
                        + t.output + "','" + t.error + "');");
            }
            if (rs.next()) {
                t.ID = rs.getInt(1);
            }

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

                    sb.append("SELECT insertPerformance(").append(t.ID).append(",").append(t.subID).append(",'")
                            .append(p.method).append("','").append(p.description).append("','")
                            .append(p.startTime).append("','").append(p.endTime).append("','")
                            .append(p.invocation).append("');").append("\n");

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

    private static void insertElementsWithoutDependency(Connection db, Task t, Set s, DBMS dbms) {
        try {
            //String sqlFooter = ");" + Utils.NEW_LINE + "COMMIT;";
            String sqlFooter = ");";

            StringBuilder query = new StringBuilder();
            //query.append("START TRANSACTION;" + Utils.NEW_LINE);

            StringBuilder sqlHeader = new StringBuilder();

            if (dbms.equals(DBMS.MEMSQL)) {
                sqlHeader.append("INSERT INTO ")
                        .append(Utils.getDataSetTableName(s.tag.toLowerCase()))
                        .append("(")
                        .append(t.transformationTag.toLowerCase())
                        .append("_task_id");
            } else {
                sqlHeader.append("INSERT INTO \"")
                        .append(Utils.getDataSetTableName(s.tag.toLowerCase()))
                        .append("\"(")
                        .append(t.transformationTag.toLowerCase())
                        .append("_task_id");
            }

            ArrayList<Attribute> tableColumns = new ArrayList<>();
            ArrayList<String> exts = new ArrayList<>();
            for (Attribute a : s.getAttributes()) {
                if (a.extractorTag == null) {
                    sqlHeader.append(",").append(a.name.toLowerCase());
                    tableColumns.add(a);
                } else {
                    String fileExtCol = Utils.getExtractorFileColumn(a.extractorTag);
                    if (!exts.contains(fileExtCol)) {
                        sqlHeader.append(",")
                                .append(fileExtCol);
                        Attribute extractorAtt = new Attribute();
                        extractorAtt.name = Utils.getExtractorFileColumn(a.extractorTag);
                        extractorAtt.extractorTag = a.extractorTag;
                        extractorAtt.type = AttributeType.EXTRACTION_TYPE;
                        tableColumns.add(extractorAtt);
                        exts.add(fileExtCol);
                    }
                }
            }
            sqlHeader.append(") VALUES (");
            query.append(sqlHeader);

            //            capacity in megabytes
            int capacity = 10000;
            StringBuilder currentQuery = new StringBuilder();
            StringBuilder sqlValues = new StringBuilder();
            boolean hasPreviousRecord = false;

            ArrayList<String> elementTuples = getElementsInCSVFormat(db, t, s, tableColumns, dbms);
            for (int elementIndex = 0; elementIndex < elementTuples.size(); elementIndex++) {
                if (hasPreviousRecord) {
                    sqlValues.append("),(\n");
                } else {
                    hasPreviousRecord = true;
                }
                sqlValues.append(elementTuples.get(elementIndex));

                if (sqlValues.length() > capacity) {
                    if (!elementTuples.isEmpty()) {
                        currentQuery = new StringBuilder();
                        currentQuery.append(query).append(sqlValues).append(sqlFooter);
//                        if (Utils.verbose) {
//                            Utils.print(2,currentQuery.toString());
//                        }
                        PreparedStatement stmt = db.prepareStatement(currentQuery.toString());
                        stmt.executeUpdate();
                        sqlValues = new StringBuilder();
                        hasPreviousRecord = false;
                    }
                }
            }

            if (sqlValues.length() > 0) {
                currentQuery = new StringBuilder();
                currentQuery.append(query).append(sqlValues).append(sqlFooter);
//                if (Utils.verbose) {
//                    Utils.print(2,currentQuery.toString());
//                }
                PreparedStatement stmt = db.prepareStatement(currentQuery.toString());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            Logger.getLogger(Provenance.class.getName()).log(Level.SEVERE, null, ex);
        } 
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

    public static ArrayList<String> getElementsInCSVFormat(Connection db, Task task,
            Set set, ArrayList<Attribute> extractionAttributes, DBMS dbms) {
        ArrayList<String> result = new ArrayList<>();
        for (Element e : task.elements) {
            if (e.setTag.equals(set.tag)) {
                result.addAll(getValuesInCSVFormat(db, set, e, extractionAttributes, task, dbms));
            }
        }
        return result;
    }

    public static ArrayList<String> getValuesInCSVFormat(Connection db, Set set, Element element,
            ArrayList<Attribute> attributes, Task task, DBMS dbms) {
        ArrayList<String> result = new ArrayList<>();


        if (element.values.size() == attributes.size()) {
            StringBuilder tuple = new StringBuilder();
            tuple.append(task.ID);
//                for (Integer value : task.dependencyIDs.values()) {
//                    tuple.append(",").append(value);
//                }

            int index = 0;
            for (int i = 0; i < attributes.size(); i++) {
                Attribute a = attributes.get(i);
//                    if ((set.type == SetType.OUTPUT && a.extractorTag != null)
//                            || set.type == SetType.INPUT) {
                String currentValue = element.values.get(i);
                index++;

                if (a.type == AttributeType.LONG_TEXT
                        || a.type == AttributeType.TEXT
                        || a.type == AttributeType.FILE) {
                    tuple.append(",'").append(currentValue).append("'");
                } else if (a.type == AttributeType.NUMERIC) {
                    tuple.append(",").append(currentValue);
                } else if (a.type == AttributeType.EXTRACTION_TYPE) {
                    String filePath = null;
                    int fileID = 0;
                    if (!set.extractors.isEmpty()) {
                        filePath = currentValue;
                        fileID = Utils.getFileID(task.files, filePath);
                        tuple.append(",").append(fileID);
                    }

                    if (!set.extractors.isEmpty()
                            && !set.propagatedExtractors.containsKey(a.extractorTag)) {
                        currentValue = currentValue.replaceAll("\r", "");
                        storeDataIntoExtractorTable(db, set, a.extractorTag, filePath, fileID, dbms);
                    }
                }
//                    }
            }

            result.add(tuple.toString());
        }

        return result;
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

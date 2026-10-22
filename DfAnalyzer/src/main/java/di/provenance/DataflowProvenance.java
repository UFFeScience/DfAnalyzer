package di.provenance;

import di.enumeration.dataflow.AttributeType;
import di.enumeration.dataflow.DataflowType;
import di.enumeration.dataflow.SetType;
import di.enumeration.dbms.DBMS;
import di.enumeration.extraction.ExtractionExtension;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import di.dataflow.object.DataflowObject;
import di.object.dataflow.Attribute;
import di.object.dataflow.Dataflow;
import di.object.dataflow.Program;
import di.object.dataflow.Set;
import di.object.dataflow.Transformation;
import di.object.extraction.Extractor;
import di.object.extraction.ExtractorCombination;
import di.object.process.Transaction;
import di.main.Utils;
import pde.json.JSONWriter;

/**
 *
 * @author vitor
 */
public class DataflowProvenance {

    public static Integer handleDataflowTransaction(Connection db, Transaction t) {
        boolean newDf = true;
        Integer dfID = null;
        HashMap<String, Integer> transformations = new HashMap<>();
        HashMap<String, Integer> sets = new HashMap<>();
        ArrayList<String> setTags = new ArrayList<>();
        ArrayList<Set> setTables = new ArrayList<>();
        for (DataflowObject o : t.getObjects()) {
            if (o.getType() == DataflowType.DATAFLOW) {
                if (Utils.verbose) {
                    Utils.print(0, "--------------------------------------------");
                    Utils.print(0, "Dataflow - " + o.dataflowTag);
                }
                Dataflow df = (Dataflow) o;
                dfID = storeDataflow(db, df, t.getDBMS());
                storeDataflowVersion(db, df, t.getDBMS());
                if (dfID == null) {
                    newDf = false;
                    dfID = df.ID;
                }
            } else if (newDf && o.getType() == DataflowType.TRANSFORMATION) {
                Transformation dt = (Transformation) o;
                if (Utils.verbose) {
                    Utils.print(1, "Transformation - " + dt.tag);
                }
                transformations.put(dt.tag, storeTransformation(db, dfID, dt, t.getDBMS()));
            } else if (newDf && o.getType() == DataflowType.PROGRAM) {
                Program p = (Program) o;
                if (Utils.verbose) {
                    Utils.print(1, "Program - " + p.name);
                }
                storeProgram(db, dfID, transformations.get(p.transformationTag), p, t.getDBMS());
            } else if (newDf && o.getType() == DataflowType.SET) {
                Set s = (Set) o;
                if (Utils.verbose) {
                    Utils.print(1, "Set - " + s.tag);
                }

                Integer depDtID = null;
                if (s.dependencyTransformation != null) {
                    depDtID = transformations.get(s.dependencyTransformation.tag);
                }

                Integer setID = storeSet(db, dfID, transformations.get(s.transformation.tag),
                        depDtID, s, t.getDBMS());
                if (setID == null) {
                    // storeSet devolve null quando falha.
                    throw new IllegalStateException("nao foi possivel gravar o conjunto '"
                            + s.tag + "' da transformacao '" + s.transformation.tag
                            + "': veja o erro de SQL acima");
                }
                sets.put(s.tag, setID);

                if (setTags.indexOf(s.tag) == -1) {
                    s.defineTaskColumns();
                    setTags.add(s.tag);
                    setTables.add(s);
                } else {
                    int index = setTags.indexOf(s.tag);
                    Set oldSet = setTables.get(index);
                    oldSet.updateTaskColumns(s);
                }
//                extractors
                HashMap<String, Integer> extractors = new HashMap<>();
                for (Extractor e : s.getExtractors()) {
                    if (Utils.verbose) {
                        Utils.print(1, "Extractor - " + e.tag + " - " + e.cartridge + " - " + e.extension);
                    }

                    storeExtractor(db, sets.get(e.setTag), e, t.getDBMS());
                    extractors.put(e.tag, e.ID);
                }
//                extractor combinations
                for (ExtractorCombination j : s.getExtractorCombinations()) {
                    if (Utils.verbose) {
                        Utils.print(1, "Extractor Combinations - " + j.outerExtractorTag + " - "
                                + j.innerExtractorTag);
                    }
                    storeExtractorCombination(db, sets.get(j.setTag),
                            extractors.get(j.outerExtractorTag),
                            extractors.get(j.innerExtractorTag),
                            j, 
                            t.getDBMS());
                }
//                attributes
                for (Attribute a : s.getAttributes()) {
                    if (Utils.verbose) {
                        Utils.print(1, "Attribute - " + a.name);
                    }
                    storeAttribute(db, sets.get(a.setTag), extractors.get(a.extractorTag), a, t.getDBMS());
                }
            }
        }

//        data set tables
        for (Set s : setTables) {
            if (Utils.verbose) {
                Utils.print(1, "Data set table - " + s.tag);
            }
            createDataSetTable(db, s, t.getDBMS());
            createDataSetView(db, s, t.getDBMS());
        }

        return dfID;
    }

    private static Integer storeDataflow(Connection db, Dataflow df, DBMS dbms) {
        try {
            Statement st = db.createStatement();

            ResultSet rs = st.executeQuery("SELECT id "
                    + "FROM dataflow "
                    + "WHERE tag='" + df.dataflowTag.toLowerCase() + "';");

            if (rs.next()) {
                df.ID = rs.getInt(1);
            }

            if (df.ID == null) {
                if (dbms.equals(DBMS.MEMSQL)) {
                    rs = insertDataflow(db, df.dataflowTag.toLowerCase());
                } else {
                    rs = st.executeQuery("SELECT insertDataflow('"
                            + df.dataflowTag.toLowerCase() + "');");
                }

                if (rs.next()) {
                    df.ID = rs.getInt(1);
                    return df.ID;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static Integer storeDataflowVersion(Connection db, Dataflow df, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertDataflowVersion(db, df.ID);
            } else {
                rs = st.executeQuery("SELECT insertDataflowVersion(" + df.ID + ");");
            }
            if (rs.next()) {
                df.version = rs.getInt(1);
                storeVersionOrigin(db, df);
                return df.version;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    /** UPDATE separado para não mudar a assinatura de insertDataflowVersion, já instalada nos bancos existentes. */
    private static void storeVersionOrigin(Connection db, Dataflow df) {
        if (df.origin == null && df.generator == null) {
            return;
        }
        try (PreparedStatement st = db.prepareStatement(
                "UPDATE dataflow_version SET origin = ?, generator = ? WHERE version = ?")) {
            st.setString(1, df.origin);
            st.setString(2, df.generator == null ? null
                    : df.generator.substring(0, Math.min(200, df.generator.length())));
            st.setInt(3, df.version);
            st.executeUpdate();
        } catch (SQLException ex) {
            Utils.print(0, "[Warning] origem do mapeamento nao gravada para a versao "
                    + df.version + ": " + ex.getMessage());
        }
    }

    private static Integer storeTransformation(Connection db, Integer dfID, Transformation dt, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertDataTransformation(db, dfID, dt.tag.toLowerCase());
            } else {
                rs = st.executeQuery("SELECT insertDataTransformation("
                        + dfID + ",'" + dt.tag.toLowerCase() + "');");
            }
            if (rs.next()) {
                dt.ID = rs.getInt(1);
                return dt.ID;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static Integer storeProgram(Connection db, Integer dfID, Integer dtID, Program p, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertProgram(db, dfID, dtID, p.name, p.path);
            } else {
                rs = st.executeQuery("SELECT insertProgram("
                        + dfID + "," + dtID + ",'" + p.name + "','" + p.path + "');");
            }
            if (rs.next()) {
                p.ID = rs.getInt(1);
                return p.ID;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static Integer storeSet(Connection db, Integer dfID, Integer dtID, Integer depDtID, Set s, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertDataSet(db, dfID, dtID, depDtID, s.tag.toLowerCase(), s.type.name());
            } else {
                rs = st.executeQuery("SELECT insertDataSet("
                        + dfID + "," + dtID + "," + depDtID + ",'" + s.tag.toLowerCase() + "','" + s.type + "');");
            }
            if (rs.next()) {
                s.ID = rs.getInt(1);
                return s.ID;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static Integer storeAttribute(Connection db, Integer setID, Integer extractorID, Attribute a, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertAttribute(db, setID, extractorID, a.name.toLowerCase(), a.type.name());
            } else {
                rs = st.executeQuery("SELECT insertAttribute("
                        + setID + "," + extractorID + ",'" + a.name.toLowerCase() + "','" + a.type + "');");
            }
            if (rs.next()) {
                a.ID = rs.getInt(1);
                return a.ID;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static boolean createDataSetTable(Connection db, Set s, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            st.execute(createDataSetSQLStatement(s, dbms));
            for (Extractor e : s.getExtractors()) {
                if (!s.propagatedExtractors.containsKey(e.tag)) {
                    st.execute(createExtractorSQLStatement(e, s, dbms));
                }
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }

    private static String createDataSetSQLStatement(Set s, DBMS dbms) {
        String SQL;
        if (dbms.equals(DBMS.MEMSQL)) {
            SQL = "CREATE TABLE " + Utils.getDataSetTableName(s.tag).toLowerCase() + " (" + Utils.NEW_LINE;
        } else {
            SQL = "CREATE SEQUENCE \"" + s.tag.toLowerCase() + "_id_seq\" as integer START WITH 1;"
                    + Utils.NEW_LINE;
            SQL += "CREATE TABLE \"" + Utils.getDataSetTableName(s.tag).toLowerCase() + "\" (" + Utils.NEW_LINE;
        }

        SQL += addIdentifiersToCreateStatement(s, dbms);

        ArrayList<String> extractors = new ArrayList<>();
        for (Attribute a : s.getAttributes()) {
            String attName = getAttributeToCreateStatement(a, !extractors.contains(a.extractorTag));
            if (attName != null) {
                SQL += attName.toLowerCase();
            }
            if (a.extractorTag != null && !extractors.contains(a.extractorTag)) {
                extractors.add(a.extractorTag);
            }
        }
        SQL += addConstraintsToCreateStatement(s, dbms);
        SQL += ");" + Utils.NEW_LINE;

        return SQL;
    }

    private static String getSequenceNameFromDataSet(Set s) {
        return s.tag.toLowerCase() + "_id_seq";
    }

    private static String addIdentifiersToCreateStatement(Set s, DBMS dbms) {
        String SQL;
        if (dbms.equals(DBMS.MEMSQL)) {
            SQL = "id BIGINT NOT NULL AUTO_INCREMENT," + Utils.NEW_LINE;
        } else {
            SQL = "id INTEGER DEFAULT NEXT VALUE FOR \"" + getSequenceNameFromDataSet(s) + "\" NOT NULL," + Utils.NEW_LINE;
        }

        for (String tc : s.previousTaskColumns) {
            SQL += tc.toLowerCase() + "_task_id INTEGER," + Utils.NEW_LINE;
        }
        for (String tc : s.nextTaskColumns) {
            SQL += tc.toLowerCase() + "_task_id INTEGER," + Utils.NEW_LINE;
        }
        return SQL;
    }

    private static String addConstraintsToCreateStatement(Set s, DBMS dbms) {
        String SQL = "";
        if (dbms.equals(DBMS.MEMSQL)) {
            SQL = "PRIMARY KEY (id)" + Utils.NEW_LINE;
        } else {
            SQL = "PRIMARY KEY (\"id\")," + Utils.NEW_LINE;
            if (s.type == SetType.OUTPUT) {
                for (String previous : s.previousTaskColumns) {
                    SQL += "FOREIGN KEY (\"" + previous.toLowerCase() + "_task_id\") REFERENCES task(\"id\") ON DELETE CASCADE ON UPDATE CASCADE," + Utils.NEW_LINE;
                }
            } else if (s.type == SetType.INPUT) {
                for (String next : s.nextTaskColumns) {
                    SQL += "FOREIGN KEY (\"" + next.toLowerCase() + "_task_id\") REFERENCES task(\"id\") ON DELETE CASCADE ON UPDATE CASCADE," + Utils.NEW_LINE;
                }
            }
            for (Extractor e : s.getExtractors()) {
                SQL += "FOREIGN KEY (\"" + Utils.getExtractorFileColumn(e.tag).toLowerCase() + "\") REFERENCES file(\"id\") ON DELETE CASCADE ON UPDATE CASCADE," + Utils.NEW_LINE;
            }
            SQL = SQL.substring(0, SQL.lastIndexOf(",")) + Utils.NEW_LINE;
        }
        return SQL;
    }

    private static String getAttributeToCreateStatement(Attribute a, boolean newExtractor) {
        if (a.extractorTag == null) {
            return getAttributeStatement(a.name, a.type);
        } else if (a.extractorTag != null && newExtractor) {
            return Utils.getExtractorFileColumn(a.extractorTag) + " INTEGER," + Utils.NEW_LINE;
        }
        return null;
    }

    private static String getAttributeStatement(String attName, AttributeType attType) {
        if (attType == AttributeType.TEXT) {
            return attName.toLowerCase() + " VARCHAR(500)," + Utils.NEW_LINE;
        } else if (attType == AttributeType.NUMERIC) {
            return attName.toLowerCase() + " DOUBLE PRECISION," + Utils.NEW_LINE;
        } else if (attType == AttributeType.FILE) {
            return attName.toLowerCase() + " VARCHAR(300)," + Utils.NEW_LINE;
        } else if (attType == AttributeType.LONG_TEXT) {
            return attName.toLowerCase() + " TEXT," + Utils.NEW_LINE;
        }

        return null;
    }

    private static String createExtractorSQLStatement(Extractor e, Set s, DBMS dbms) {
        String SQL;
        if (dbms.equals(DBMS.MEMSQL)) {
            SQL = "CREATE TABLE " + Utils.getExtractorTableName(e.tag).toLowerCase() + "(" + Utils.NEW_LINE;
        } else {
            SQL = "CREATE SEQUENCE \"" + Utils.getSequenceNameFromExtractor(e.tag) + "\" as integer START WITH 1;" + Utils.NEW_LINE;
            SQL += "CREATE TABLE \"" + Utils.getExtractorTableName(e.tag).toLowerCase() + "\" (" + Utils.NEW_LINE;
        }
        SQL += addIdentifiersToCreateStatement(e, s, dbms);
        SQL += addConstraintsToCreateStatement(e, s, dbms);
        SQL += ");" + Utils.NEW_LINE;
        return SQL;
    }

    private static String addIdentifiersToCreateStatement(Extractor e, Set s, DBMS dbms) {
        String SQL;
        if (dbms.equals(DBMS.MEMSQL)){
            SQL = e.tag.toLowerCase() + "_id INTEGER NOT NULL AUTO_INCREMENT," + Utils.NEW_LINE;
        } else {
            SQL = e.tag.toLowerCase() + "_id INTEGER DEFAULT NEXT VALUE FOR \"" + Utils.getSequenceNameFromExtractor(e.tag) + "\" NOT NULL," + Utils.NEW_LINE;
        }
        SQL += Utils.getExtractorFileColumn(e.tag) + " INTEGER NOT NULL," + Utils.NEW_LINE;
        SQL += Utils.getExtractorFileNameColumn() + " VARCHAR(200)," + Utils.NEW_LINE;
        
        if(e.extension == ExtractionExtension.FASTBIT){
            SQL += getAttributeStatement("rowid", AttributeType.NUMERIC);
        }else if(e.extension == ExtractionExtension.POSTGRES_RAW){                                    
            SQL += getAttributeStatement("tablename", AttributeType.TEXT);
        }

        ArrayList<String> atts = new ArrayList<>();
        if (e.extension != ExtractionExtension.POSTGRES_RAW) {//by thaylon, edited to support postgres_raw            
            for (Attribute a : s.getAttributes()) {
                if (a.extractorTag != null && a.extractorTag.equals(e.tag)
                        && !a.name.toLowerCase().equals("filename")) {
                    atts.add(a.name.toLowerCase());
                    if (e.extension == ExtractionExtension.FASTBIT
                            || e.extension == ExtractionExtension.OPTIMIZED_FASTBIT
                            || e.extension == ExtractionExtension.POSTGRES_RAW) {
                        a.type = AttributeType.TEXT;
                    }

                    SQL += getAttributeStatement(a.name, a.type);
                }
            }
        }
        
        for (ExtractorCombination ec : s.extractorCombinations) {
            if (ec.innerExtractorTag.equals(e.tag) || ec.outerExtractorTag.equals(e.tag)) {
                for (int i = 0; i < ec.keys.length; i++) {
                    String key = ec.keys[i].toLowerCase();
                    if (!atts.contains(key)) {
                        SQL += getAttributeStatement(key, ec.keyTypes[i]);
                        i++;
                    }
                }
            }
        }
        return SQL;
    }

    private static String addConstraintsToCreateStatement(Extractor e, Set s, DBMS dbms) {
        String SQL;
        if (dbms.equals(DBMS.MEMSQL)) {
            SQL = "PRIMARY KEY (" + e.tag.toLowerCase() + "_id)" + Utils.NEW_LINE;
        } else {
            SQL = "PRIMARY KEY (\"" + e.tag.toLowerCase() + "_id\")," + Utils.NEW_LINE;
            SQL += "FOREIGN KEY (\"" + Utils.getExtractorFileColumn(e.tag) + "\") REFERENCES file(\"id\") ON DELETE CASCADE ON UPDATE CASCADE," + Utils.NEW_LINE;
            SQL = SQL.substring(0, SQL.lastIndexOf(",")) + Utils.NEW_LINE;
        }
        return SQL;
    }

    private static Integer storeExtractor(Connection db, Integer dsID, Extractor e, DBMS dbms) {
        try {
            Statement st = db.createStatement();

            ResultSet rs;
            if (dbms.equals(DBMS.MEMSQL)) {
                rs = insertExtractor(db, e.tag, dsID, e.cartridge.name(), e.extension.name());
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("SELECT insertExtractor('").append(e.tag).append("',").append(dsID).append(",'").append(e.cartridge).append("','").append(e.extension).append("')").append("\n");
                rs = st.executeQuery(sb.toString());
            }

            if (rs.next()) {
                e.ID = rs.getInt(1);
                return e.ID;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    private static Integer storeExtractorCombination(Connection db, Integer dsID, Integer outerExtID,
            Integer innerExtID, ExtractorCombination j, DBMS dbms) {
        if (outerExtID != null && innerExtID != null) {
            try {
                Statement st = db.createStatement();

                ResultSet rs;
                if (dbms.equals(DBMS.MEMSQL)) {
                    rs = insertExtractorCombination(db, outerExtID, innerExtID, dsID, j.getKeysAsString(), j.getKeyTypesAsString());
                } else {
                    StringBuilder sb = new StringBuilder();

                    sb.append("SELECT insertExtractorCombination(").append(outerExtID).append(",")
                            .append(innerExtID).append(",").append(dsID).append(",'")
                            .append(j.getKeysAsString()).append("','")
                            .append(j.getKeyTypesAsString()).append("');").append("\n");

                    rs = st.executeQuery(sb.toString());
                }
                if (rs.next()) {
                    j.ID = rs.getInt(1);
                    return j.ID;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return null;
    }

    private static void createDataSetView(Connection db, Set s, DBMS dbms) {
        try {
            Statement st = db.createStatement();
            StringBuilder sb = getSQLToCreateDataSetView(s, dbms);

//            if (Utils.verbose) {
//                System.out.println(sb.toString());
//            }
            st.execute(sb.toString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static StringBuilder getSQLToCreateDataSetView(Set s, DBMS dbms) {
        StringBuilder sb = new StringBuilder();
//            create view

        if (dbms.equals(DBMS.MEMSQL)) {
            sb.append("CREATE VIEW ").append(s.tag.toLowerCase()).append(" AS").append(Utils.NEW_LINE);
            sb.append("SELECT ")
                    .append("ds.id").append(" AS id");

            for (String dtTag : s.previousTaskColumns) {
                sb.append(", ds.").append(dtTag).append("_task_id AS ").append(dtTag).append("_task_id");
            }
            for (String dtTag : s.nextTaskColumns) {
                sb.append(", ds.").append(dtTag).append("_task_id AS ").append(dtTag).append("_task_id");
            }

            for (Extractor e : s.extractors.values()) {
                sb.append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorFileColumn(e.tag)).append(" AS ").append(Utils.getExtractorFileColumn(e.tag))
                        .append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorIDColumn(e.tag)).append(" AS ").append(Utils.getExtractorIDColumn(e.tag))
                        .append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorFileNameColumn()).append(" AS ").append(e.tag).append("_").append(Utils.getExtractorFileNameColumn());

            }
            for (Attribute a : s.attributes) {
                if (a.extractorTag == null) {
                    sb.append(", ds.").append(a.name.toLowerCase()).append(" AS ").append(a.name.toLowerCase());
                } else {
                    sb.append(", ").append(a.extractorTag.toLowerCase()).append(".").append(a.name.toLowerCase()).append(" AS ").append(a.name.toLowerCase());
                }
            }
        } else {
            sb.append("CREATE VIEW ").append(s.tag.toLowerCase()).append("(");
            sb.append("id");

            for (String dtTag : s.previousTaskColumns) {
                sb.append(", ").append(dtTag).append("_task_id");
            }
            for (String dtTag : s.nextTaskColumns) {
                sb.append(", ").append(dtTag).append("_task_id");
            }

            List<String> extractorsPostgresRAW = new ArrayList<>();
            
            for (Extractor e : s.extractors.values()) {//TODO by thaylon
                sb.append(", ").append(Utils.getExtractorFileColumn(e.tag))
                        .append(", ").append(Utils.getExtractorIDColumn(e.tag))
                        .append(", ").append(e.tag).append("_")
                        .append(Utils.getExtractorFileNameColumn());
                if(e.extension == ExtractionExtension.POSTGRES_RAW){
                 extractorsPostgresRAW.add(e.setTag);
                }
            }
            
            for (Attribute a : s.attributes) {
                if (!extractorsPostgresRAW.contains(a.setTag)) {
                    sb.append(", ").append(a.name.toLowerCase());
                }
            }

            sb.append(") AS").append(Utils.NEW_LINE);

//            select
            sb.append("SELECT ")
                    .append("ds.id");

            for (String dtTag : s.previousTaskColumns) {
                sb.append(", ds.").append(dtTag).append("_task_id");
            }
            for (String dtTag : s.nextTaskColumns) {
                sb.append(", ds.").append(dtTag).append("_task_id");
            }

            for (Extractor e : s.extractors.values()) {
                sb.append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorFileColumn(e.tag))
                        .append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorIDColumn(e.tag))
                        .append(", ").append(e.tag.toLowerCase()).append(".").append(Utils.getExtractorFileNameColumn());
            }
            for (Attribute a : s.attributes) {
                if (!extractorsPostgresRAW.contains(a.setTag)) {
                    if (a.extractorTag == null) {
                        sb.append(", ds.").append(a.name.toLowerCase());
                    } else {
                        sb.append(", ").append(a.extractorTag.toLowerCase()).append(".").append(a.name.toLowerCase());
                    }
                }
            }
        }

        sb.append(Utils.NEW_LINE)
                .append(" FROM ")
                .append(Utils.getDataSetTableName(s.tag))
                .append(" AS ds");
        for (Extractor e : s.extractors.values()) {
            sb.append(", ")
                    .append(Utils.getExtractorTableName(e.tag))
                    .append(" AS ")
                    .append(e.tag);
        }
        sb.append(Utils.NEW_LINE);

        if (s.extractors.size() > 0) {
            sb.append(" WHERE ");
            boolean first = true;
            for (Extractor e : s.extractors.values()) {
                if (first) {
                    first = false;
                } else {
                    sb.append(Utils.NEW_LINE).append(" AND ");
                }
                sb.append("ds.").append(Utils.getExtractorFileColumn(e.tag))
                        .append("=")
                        .append(e.tag).append(".").append(Utils.getExtractorFileColumn(e.tag));
            }
            for (ExtractorCombination ec : s.extractorCombinations) {
                for (String key : ec.keys) {
                    sb.append(Utils.NEW_LINE).append(" AND ")
                            .append(ec.innerExtractorTag).append(".").append(key)
                            .append("=")
                            .append(ec.outerExtractorTag).append(".").append(key);
                }
            }
        }
        sb.append(";");
        return sb;
    }

    private static ResultSet insertDataflow(Connection db, String tag) throws SQLException {
        PreparedStatement st = db.prepareStatement("INSERT INTO dataflow(tag) VALUES(?);", Statement.RETURN_GENERATED_KEYS);
        st.setString(1, tag);
        st.executeUpdate();
        ResultSet rs = st.getGeneratedKeys();
        return rs;
    }

    private static ResultSet insertDataflowVersion(Connection db, Integer df_id) throws SQLException {
        PreparedStatement st = db.prepareStatement("INSERT INTO dataflow_version(df_id) VALUES(?);", Statement.RETURN_GENERATED_KEYS);
        st.setInt(1, df_id);
        st.executeUpdate();
        ResultSet rs = st.getGeneratedKeys();
        return rs;
    }

    private static ResultSet insertDataTransformation(Connection db, Integer df_id, String tag) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM data_transformation WHERE df_id = (?) AND tag = (?);");
        st.setInt(1, df_id);
        st.setString(2, tag);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO data_transformation(df_id,tag) VALUES(?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, df_id);
            st.setString(2, tag);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertProgram(Connection db, Integer df_id, Integer dt_id, String name, String path) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM program WHERE df_id = (?) AND name = (?) AND path = (?);");
        st.setInt(1, df_id);
        st.setString(2, name);
        st.setString(3, path);
        ResultSet rs = st.executeQuery();

        Integer program_id;
        if (rs.next()) {
            program_id = rs.getInt("id");
        } else {
            st = db.prepareStatement("INSERT INTO program(df_id,name,path) VALUES(?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, df_id);
            st.setString(2, name);
            st.setString(3, path);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
            rs.next();
            program_id = rs.getInt(1);
        }

        st = db.prepareStatement("INSERT INTO use_program(dt_id,program_id) VALUES(?,?);");
        st.setInt(1, dt_id);
        st.setInt(2, program_id);
        st.executeUpdate();

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertDataSet(Connection db, Integer df_id, Integer dt_id, Integer dep_dt_id, String tag, String type) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM data_set WHERE df_id = (?) AND tag = (?);");
        st.setInt(1, df_id);
        st.setString(2, tag);
        ResultSet rs = st.executeQuery();

        Integer ds_id;
        if (rs.next()) {
            ds_id = rs.getInt("id");
        } else {
            st = db.prepareStatement("INSERT INTO data_set(df_id,tag) VALUES(?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, df_id);
            st.setString(2, tag);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
            rs.next();
            ds_id = rs.getInt(1);
        }

        if (dep_dt_id != null) {
            st = db.prepareStatement("SELECT ds_id FROM data_dependency"
                    + " WHERE previous_dt_id = (?) AND next_dt_id = (?) AND ds_id = (?);");
            st.setInt(1, dep_dt_id);
            st.setInt(2, dt_id);
            st.setInt(3, ds_id);
            st.executeQuery();
            // Changed

            Integer vid;
            st = db.prepareStatement("SELECT id FROM data_dependency"
                    + " WHERE previous_dt_id = (?) AND next_dt_id IS NULL;");
            st.setInt(1, dep_dt_id);
            ResultSet rs1 = st.executeQuery();
            if (rs1.next()) {
                vid = rs1.getInt(1);
                st = db.prepareStatement("UPDATE data_dependency SET next_dt_id = (?) WHERE id = (?);");
                st.setInt(1, dt_id);
                st.setInt(2, vid);
                st.executeUpdate();
            } else {
                st = db.prepareStatement("INSERT INTO data_dependency(previous_dt_id,next_dt_id,ds_id) VALUES(?,?,?);");
                st.setInt(1, dep_dt_id);
                st.setInt(2, dt_id);
                st.setInt(3, ds_id);
                st.executeUpdate();
            }
        } else {
            if (type.equals("INPUT")) {
                st = db.prepareStatement("INSERT INTO data_dependency(previous_dt_id,next_dt_id,ds_id) VALUES(?,?,?);");
                st.setNull(1, Types.INTEGER);
                st.setInt(2, dt_id);
                st.setInt(3, ds_id);
                st.executeUpdate();
            } else {
                st = db.prepareStatement("INSERT INTO data_dependency(previous_dt_id,next_dt_id,ds_id) VALUES(?,?,?);");
                st.setInt(1, dt_id);
                st.setNull(2, Types.INTEGER);
                st.setInt(3, ds_id);
                st.executeUpdate();
            }
        }
        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertAttribute(Connection db, Integer dds_id, Integer vextractor_id, String vname, String vtype) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM attribute WHERE ds_id = (?) AND name = (?);");
        st.setInt(1, dds_id);
        st.setString(2, vname);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO attribute(ds_id,extractor_id,name,type) VALUES(?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, dds_id);
            if (vextractor_id == null) {
                st.setNull(2, Types.INTEGER);
            } else {
                st.setInt(2, vextractor_id);
            }
            st.setString(3, vname);
            st.setString(4, vtype);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertExtractor(Connection db, String vtag, Integer vds_id, String vcartridge, String vextension) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM extractor WHERE tag = (?) AND ds_id = (?) AND cartridge = (?) AND extension = (?);");
        st.setString(1, vtag);
        st.setInt(2, vds_id);
        st.setString(3, vcartridge);
        st.setString(4, vextension);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO extractor(ds_id,tag,cartridge,extension) VALUES(?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, vds_id);
            st.setString(2, vtag);
            st.setString(3, vcartridge);
            st.setString(4, vextension);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }

    private static ResultSet insertExtractorCombination(Connection db, Integer vouter_ext_id, Integer vinner_ext_id, Integer vds_id, String vkeys, String vkey_types) throws SQLException {
        PreparedStatement st = db.prepareStatement("SELECT id FROM extractor_combination WHERE outer_ext_id = (?) AND inner_ext_id = (?) AND ds_id = (?);");
        st.setInt(1, vouter_ext_id);
        st.setInt(2, vinner_ext_id);
        st.setInt(3, vds_id);
        ResultSet rs = st.executeQuery();

        if (!rs.next()) {
            st = db.prepareStatement("INSERT INTO extractor_combination(outer_ext_id,inner_ext_id,keys_,key_types,ds_id) VALUES(?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
            st.setInt(1, vouter_ext_id);
            st.setInt(2, vinner_ext_id);
            st.setString(3, vkeys);
            st.setString(4, vkey_types);
            st.setInt(5, vds_id);
            st.executeUpdate();
            rs = st.getGeneratedKeys();
        }

        rs.beforeFirst();
        return rs;
    }
}

package di.provenance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import di.enumeration.dataflow.SetType;
import di.object.dataflow.Attribute;
import di.object.dataflow.Dataflow;
import di.object.dataflow.Set;
import di.object.dataflow.Transformation;
import di.enumeration.dataflow.AttributeType;

/**
 * Reconstrói um Dataflow a partir da proveniência prospectiva já gravada no banco.
 *
 * @author joao
 */
public final class DataflowLoader {

    private DataflowLoader() {
    }

    private static final String TRANSFORMATIONS_SQL =
            "SELECT dt.id, dt.tag FROM data_transformation dt, dataflow df"
            + " WHERE dt.df_id = df.id AND df.tag = ?;";

    private static final String DEPENDENCIES_SQL =
            "SELECT dd.previous_dt_id, dd.next_dt_id, ds.id, ds.tag"
            + " FROM data_dependency dd, data_set ds, dataflow df"
            + " WHERE dd.ds_id = ds.id AND ds.df_id = df.id AND df.tag = ?"
            + " ORDER BY dd.id;";

    private static final String ATTRIBUTES_SQL =
            "SELECT a.ds_id, a.name, a.type, e.tag"
            + " FROM attribute a LEFT OUTER JOIN extractor e ON a.extractor_id = e.id,"
            + " data_set ds, dataflow df"
            + " WHERE a.ds_id = ds.id AND ds.df_id = df.id AND df.tag = ?"
            + " ORDER BY a.id;";

    public static Dataflow load(Connection db, String dataflowTag) {
        if (db == null || dataflowTag == null || dataflowTag.isEmpty()) {
            return null;
        }
        try {
            Map<Integer, Transformation> transformations = loadTransformations(db, dataflowTag);
            if (transformations.isEmpty()) {
                return null;
            }

            Dataflow dataflow = new Dataflow();
            dataflow.dataflowTag = dataflowTag;
            dataflow.transformations.addAll(transformations.values());

            Map<Integer, java.util.List<Attribute>> attributes = loadAttributes(db, dataflowTag);
            loadSets(db, dataflowTag, dataflow, transformations, attributes);
            return dataflow;
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "falha ao reconstruir o dataflow '" + dataflowTag + "' a partir do banco", ex);
        }
    }

    private static Map<Integer, Transformation> loadTransformations(Connection db, String dataflowTag)
            throws SQLException {
        Map<Integer, Transformation> byId = new HashMap<>();
        try (PreparedStatement st = db.prepareStatement(TRANSFORMATIONS_SQL)) {
            st.setString(1, dataflowTag);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Transformation transformation = new Transformation();
                    transformation.tag = rs.getString(2);
                    byId.put(rs.getInt(1), transformation);
                }
            }
        }
        return byId;
    }

    private static Map<Integer, java.util.List<Attribute>> loadAttributes(Connection db, String dataflowTag)
            throws SQLException {
        Map<Integer, java.util.List<Attribute>> byDataset = new HashMap<>();
        try (PreparedStatement st = db.prepareStatement(ATTRIBUTES_SQL)) {
            st.setString(1, dataflowTag);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int datasetId = rs.getInt(1);
                    Attribute attribute = new Attribute();
                    attribute.name = rs.getString(2);
                    attribute.type = AttributeType.valueOf(rs.getString(3).toUpperCase());
                    attribute.extractorTag = rs.getString(4);

                    java.util.List<Attribute> list = byDataset.get(datasetId);
                    if (list == null) {
                        list = new java.util.ArrayList<>();
                        byDataset.put(datasetId, list);
                    }
                    list.add(attribute);
                }
            }
        }
        return byDataset;
    }

    private static void loadSets(Connection db, String dataflowTag, Dataflow dataflow,
            Map<Integer, Transformation> transformations,
            Map<Integer, java.util.List<Attribute>> attributes) throws SQLException {

        try (PreparedStatement st = db.prepareStatement(DEPENDENCIES_SQL)) {
            st.setString(1, dataflowTag);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Integer previousId = (Integer) rs.getObject(1);
                    Integer nextId = (Integer) rs.getObject(2);
                    int datasetId = rs.getInt(3);
                    String datasetTag = rs.getString(4);

                    Transformation producer = previousId == null ? null : transformations.get(previousId);
                    Transformation consumer = nextId == null ? null : transformations.get(nextId);

                    if (producer != null) {
                        // Saída do produtor: é ela que INSERE a linha de dados.
                        dataflow.sets.add(buildSet(datasetTag, SetType.OUTPUT, producer, null,
                                attributes.get(datasetId)));
                    }
                    if (consumer != null) {
                        // Entrada do consumidor: com dependência, ATUALIZA a mesma linha.
                        dataflow.sets.add(buildSet(datasetTag, SetType.INPUT, consumer, producer,
                                attributes.get(datasetId)));
                    }
                }
            }
        }
    }

    private static Set buildSet(String tag, SetType type, Transformation transformation,
            Transformation dependency, java.util.List<Attribute> attributes) {
        Set set = new Set();
        set.tag = tag;
        set.type = type;
        set.transformation = transformation;
        set.dependencyTransformation = dependency;

        if (attributes != null) {
            for (Attribute attribute : attributes) {
                Attribute copy = new Attribute();
                copy.name = attribute.name;
                copy.type = attribute.type;
                copy.extractorTag = attribute.extractorTag;
                copy.setTag = tag;
                copy.transformationTag = transformation.tag;
                set.addAttribute(copy);
            }
        }

        set.defineTaskColumns();

        if (type == SetType.INPUT) {
            transformation.inputSets.put(tag, set);
        } else {
            transformation.outputSets.put(tag, set);
        }
        return set;
    }
}

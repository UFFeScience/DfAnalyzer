package rest.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import qp.dataflow.Attribute;
import qp.dataflow.Dataflow;
import qp.dataflow.Dataset;
import qp.dataflow.Dependency;
import qp.dataflow.Transformation;

/**
 * Exporta a proveniência prospectiva em PROV-JSON (W3C PROV-DM).
 *
 * @author joao
 */
@Service
public class ProvExporter {

    private static final String NS = "dfa";

    public Map<String, Object> toProvJson(String dataflowTag, int version, Dataflow dataflow) {
        Map<String, Object> document = new LinkedHashMap<>();

        Map<String, String> prefix = new LinkedHashMap<>();
        prefix.put("prov", "http://www.w3.org/ns/prov#");
        prefix.put(NS, "urn:dfanalyzer:" + dataflowTag + ":v" + version + ":");
        document.put("prefix", prefix);

        Map<String, Object> entities = new LinkedHashMap<>();
        for (Dataset dataset : dataflow.getDatasets()) {
            entities.put(entityId(dataset.getTag()), describeDataset(dataset));
        }
        document.put("entity", entities);

        Map<String, Object> activities = new LinkedHashMap<>();
        for (Transformation transformation : dataflow.getTransformations()) {
            Map<String, Object> activity = new LinkedHashMap<>();
            activity.put("prov:label", transformation.getTag());
            activity.put(NS + ":tag", transformation.getTag());
            activities.put(activityId(transformation.getTag()), activity);
        }
        document.put("activity", activities);

        Map<String, Object> used = new LinkedHashMap<>();
        Map<String, Object> generated = new LinkedHashMap<>();

        for (Dependency dependency : dataflow.getDependencies()) {
            Dataset dataset = dependency.getDataset();
            Transformation producer = dependency.getPreviousTransformation();
            Transformation consumer = dependency.getNextTransformation();

            if (producer != null) {
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("prov:entity", entityId(dataset.getTag()));
                relation.put("prov:activity", activityId(producer.getTag()));
                generated.put(NS + ":gen_" + producer.getTag() + "_" + dataset.getTag(), relation);
            }
            if (consumer != null) {
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("prov:activity", activityId(consumer.getTag()));
                relation.put("prov:entity", entityId(dataset.getTag()));
                used.put(NS + ":used_" + consumer.getTag() + "_" + dataset.getTag(), relation);
            }
        }

        document.put("used", used);
        document.put("wasGeneratedBy", generated);
        return document;
    }

    private static Map<String, Object> describeDataset(Dataset dataset) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("prov:label", dataset.getTag());
        entity.put(NS + ":tag", dataset.getTag());

        List<Map<String, String>> attributes = new ArrayList<>();
        if (dataset.getAttributes() != null) {
            for (Attribute attribute : dataset.getAttributes()) {
                Map<String, String> described = new LinkedHashMap<>();
                described.put("name", attribute.getName());
                described.put("type", attribute.getAttributeType() == null
                        ? null : attribute.getAttributeType().name());
                attributes.add(described);
            }
        }
        entity.put(NS + ":attributes", attributes);
        return entity;
    }

    private static String entityId(String tag) {
        return NS + ":ds_" + tag;
    }

    private static String activityId(String tag) {
        return NS + ":dt_" + tag;
    }
}

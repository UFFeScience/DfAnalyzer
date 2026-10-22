package di.json;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import di.object.dataflow.Attribute;
import di.enumeration.dataflow.AttributeType;
import di.object.dataflow.Dataflow;
import di.object.task.Element;
import di.enumeration.extraction.ExtractionCartridge;
import di.enumeration.extraction.ExtractionExtension;
import di.object.extraction.Extractor;
import di.object.extraction.ExtractorCombination;
import di.object.dataflow.Program;
import di.object.dataflow.Set;
import di.enumeration.dataflow.SetType;
import di.enumeration.dbms.DBMS;
import di.object.task.Task;
import di.object.dataflow.Transformation;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import di.object.process.Transaction;
import di.enumeration.process.TransactionType;
import di.object.task.Performance;
import di.object.task.dfFile;
import di.main.Utils;

/**
 *
 * @author vitor, debora, vinicius
 */
public class JSONReader {

    public static JSONObject readDataflow(String filePath) {
        try {
            JSONParser parser = new JSONParser();
            FileReader fr = new FileReader(filePath);
            JSONObject obj = (JSONObject) parser.parse(fr);
            fr.close();
            return obj;
        } catch (IOException | ParseException ex) {
            return null;
        }
    }

    public static JSONObject readDataflowFromRequest(String df_string) {
        try {
            JSONParser parser = new JSONParser();
            return (JSONObject) parser.parse(df_string);
        } catch (ParseException ex) {
            Logger.getLogger(JSONReader.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static JSONObject getDataTransformation(JSONObject df, String dataTransformationTag) {
        JSONArray dts = (JSONArray) df.get("transformations");
        if (dts != null) {
            ListIterator iDts = dts.listIterator();
            while (iDts.hasNext()) {
                JSONObject dt = (JSONObject) iDts.next();
                if (dt.get("tag").equals(dataTransformationTag)) {
                    return dt;
                }
            }
        }

        return null;
    }

    public static JSONObject getSet(JSONObject df, String transformationTag, String setTag) {
        JSONObject dt = getDataTransformation(df, transformationTag);
        JSONArray dss = (JSONArray) dt.get("sets");
        if (dss != null) {
            ListIterator iDss = dss.listIterator();
            while (iDss.hasNext()) {
                JSONObject ds = (JSONObject) iDss.next();
                if ((ds.get("tag") != null && ds.get("tag").equals(setTag))
                        || (ds.get("dependency") != null && ds.get("dependency").equals(setTag))) {
                    return ds;
                }
            }
        }

        return null;
    }

    static JSONObject getSet(JSONObject task, String setTag) {
        JSONArray dss = (JSONArray) task.get("sets");
        if (dss != null) {
            ListIterator iDss = dss.listIterator();
            while (iDss.hasNext()) {
                JSONObject ds = (JSONObject) iDss.next();
                if ((ds.get("tag") != null && ds.get("tag").equals(setTag))) {
                    return ds;
                }
            }
        }

        return null;
    }

    static JSONObject getFile(JSONObject task, dfFile file) {
        JSONArray files = (JSONArray) task.get("files");
        if (files != null) {
            ListIterator iFiles = files.listIterator();
            while (iFiles.hasNext()) {
                JSONObject f = (JSONObject) iFiles.next();
                if (f.get("name") != null && f.get("path") != null
                        && f.get("name").equals(file.name) && f.get("path").equals(file.path)) {
                    return f;
                }
            }
        }

        return null;
    }

    static JSONObject getTask(String dataflowAnalyzerDirectory, String dataflowTag, String transformationTag, Integer taskID) {
        try {
            JSONParser parser = new JSONParser();
            FileReader fr = new FileReader(Utils.getTaskFilePath(dataflowAnalyzerDirectory, dataflowTag, transformationTag, taskID));
            JSONObject obj = (JSONObject) parser.parse(fr);
            fr.close();
            return obj;
        } catch (ParseException ex) {
            Logger.getLogger(JSONReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (FileNotFoundException ex) {

        } catch (IOException ex) {
            Logger.getLogger(JSONReader.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public static JSONObject getTaskFromRequest(String request) {

          // Notar que nesta abordagem, não sabemos a que workflow pertence este json
        try {
            JSONParser parser = new JSONParser();
              //FileReader fr = new FileReader(Utils.getTaskFilePath(dataflowAnalyzerDirectory, dataflowTag, transformationTag, taskID));
            //JSONObject obj = (JSONObject) parser.parse(fr);
            //fr.close();
            JSONObject obj = (JSONObject) parser.parse(request);
            return obj;
        } catch (ParseException ex) {
            Logger.getLogger(JSONReader.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    static String normalizeOrigin(Object raw) {
        if (!(raw instanceof String)) {
            return null;
        }
        String origin = ((String) raw).trim().toLowerCase();
        if (origin.equals("manual") || origin.equals("generated") || origin.equals("curated")) {
            return origin;
        }
        if (!origin.isEmpty()) {
            Logger.getLogger(JSONReader.class.getName()).log(Level.WARNING,
                    "origin desconhecida no dataflow: ''{0}'' - gravada como nao informada", raw);
        }
        return null;
    }

    public static Transaction generationDataflowTransaction(String fileName, String path, JSONObject dfJSON, DBMS dbms) {
        Transaction t = new Transaction(TransactionType.DATAFLOW, fileName, path, dbms);

        Dataflow df = new Dataflow();
        df.dataflowTag = ((String) dfJSON.get("tag")).toLowerCase();
        df.origin = normalizeOrigin(dfJSON.get("origin"));
        Object generator = dfJSON.get("generator");
        df.generator = generator instanceof String && !((String) generator).trim().isEmpty()
                ? (String) generator : null;
        t.addObject(df);

        JSONArray dts = (JSONArray) dfJSON.get("transformations");
        if (dts != null) {
            Iterator<JSONObject> it = dts.iterator();
            while (it.hasNext()) {
                JSONObject dtJSON = it.next();

                Transformation dt = new Transformation();
                dt.dataflowTag = df.dataflowTag.toLowerCase();
                dt.tag = ((String) dtJSON.get("tag")).toLowerCase();
                df.transformations.add(dt);
                t.addObject(dt);

                JSONArray programs = (JSONArray) dtJSON.get("programs");
                if (programs != null) {
                    Iterator<JSONObject> itPrograms = programs.iterator();
                    while (itPrograms.hasNext()) {
                        JSONObject programJSON = itPrograms.next();

                        Program p = new Program();
                        p.dataflowTag = df.dataflowTag.toLowerCase();
                        p.transformationTag = dt.tag.toLowerCase();
                        p.name = (String) programJSON.get("name");
                        p.path = (String) programJSON.get("path");
                        t.addObject(p);
                    }
                }

                JSONArray dss = (JSONArray) dtJSON.get("sets");
                if (dss != null) {
                    Iterator<JSONObject> itDss = dss.iterator();
                    while (itDss.hasNext()) {
                        JSONObject dsJSON = itDss.next();

                        Set s = new Set();
                        s.dataflowTag = df.dataflowTag.toLowerCase();
                        s.transformation = dt;
                        s.tag = ((String) dsJSON.get("tag")).toLowerCase();
                        s.type = SetType.valueOf(dsJSON.get("type").toString().toUpperCase());

                        String depDtTag = (String) dsJSON.get("dependency");
                        if (depDtTag != null) {
                            s.dependencyTransformation = df.getTransformation(depDtTag.toLowerCase());
                        } else {
                            JSONArray atts = (JSONArray) dsJSON.get("attributes");
                            if (atts != null) {
                                Iterator<JSONObject> itAtts = atts.iterator();
                                while (itAtts.hasNext()) {
                                    JSONObject attJSON = itAtts.next();

                                    Attribute att = new Attribute();
                                    att.dataflowTag = df.dataflowTag.toLowerCase();
                                    att.transformationTag = dt.tag.toLowerCase();
                                    att.setTag = s.tag.toLowerCase();
                                    att.name = ((String) attJSON.get("name")).toLowerCase();
                                    att.type = AttributeType.valueOf(((String) attJSON.get("type")));
                                    String extName = (String) attJSON.get("extractor");
                                    if (extName != null) {
                                        att.extractorTag = (extName).toLowerCase();
                                    }
                                    s.addAttribute(att);
                                }

                                JSONArray exts = (JSONArray) dsJSON.get("extractors");
                                if (exts != null) {
                                    Iterator<JSONObject> itExts = exts.iterator();
                                    while (itExts.hasNext()) {
                                        JSONObject extJSON = itExts.next();

                                        Extractor ext = new Extractor();
                                        ext.dataflowTag = df.dataflowTag.toLowerCase();
                                        ext.transformationTag = dt.tag.toLowerCase();
                                        ext.setTag = s.tag.toLowerCase();
                                        ext.tag = ((String) extJSON.get("tag")).toLowerCase();
                                        ext.cartridge = ExtractionCartridge.valueOf((String) extJSON.get("cartridge"));
                                        ext.extension = ExtractionExtension.valueOf((String) extJSON.get("extension"));
                                        s.addExtractor(ext);
                                    }
                                }

                                JSONArray js = (JSONArray) dsJSON.get("extractor.combinations");
                                if (js != null) {
                                    Iterator<JSONObject> itJs = js.iterator();
                                    while (itJs.hasNext()) {
                                        JSONObject combinationJSON = itJs.next();

                                        ExtractorCombination j = new ExtractorCombination();
                                        j.dataflowTag = df.dataflowTag.toLowerCase();
                                        j.transformationTag = dt.tag.toLowerCase();
                                        j.setTag = s.tag.toLowerCase();
                                        j.outerExtractorTag = ((String) combinationJSON.get("outer")).toLowerCase();
                                        j.innerExtractorTag = ((String) combinationJSON.get("inner")).toLowerCase();
                                        j.keys = ((String) combinationJSON.get("keys")).toLowerCase().split(Utils.ELEMENT_SEPARATOR);
                                        String[] types = ((String) combinationJSON.get("key_types")).split(Utils.ELEMENT_SEPARATOR);
                                        j.keyTypes = new AttributeType[types.length];
                                        int i = 0;
                                        for (String tp : types) {
                                            j.keyTypes[i] = AttributeType.valueOf(tp.toUpperCase());
                                            i++;
                                        }
                                        s.addExtractorCombination(j);
                                    }
                                }
                            }
                        }

                        if (s.type == SetType.INPUT) {
                            dt.inputSets.put(s.tag, s);
                        } else if (s.type == SetType.OUTPUT) {
                            dt.outputSets.put(s.tag, s);
                        }

                        df.sets.add(s);
                        t.addObject(s);
                    }
                }
            }
        }

//        update extractors
        for (Set s : df.sets) {
            if (s.dependencyTransformation != null) {
                updateSetWithDataDependency(s);
            } else if (s.type == SetType.OUTPUT) {
                updateOutputSet(s);
            }
        }

        return t;
    }

    private static void updateSetWithDataDependency(Set s) {
        for (Set depOutSet : s.dependencyTransformation.outputSets.values()) {
            if (s.tag.equals(depOutSet.tag)) {
                s.attributes.addAll(depOutSet.attributes);

                for (Extractor e : depOutSet.extractors.values()) {
                    if (s.extractors.get(e.tag) == null) {
                        s.extractors.put(e.tag, e);
                        s.propagatedExtractors.put(e.tag, e);
                    }
                }
            }
        }

        for (Set outSet : s.transformation.outputSets.values()) {
            for (Attribute a : outSet.getAttributes()) {
                for (Set depOutSet : s.dependencyTransformation.outputSets.values()) {
                    if (depOutSet.hasAttribute(a.name)
                            && depOutSet.getAttribute(a.name).equals(a.name)
                            && !outSet.extractors.containsKey(a.extractorTag)) {
                        Extractor e = s.getExtractor(a.extractorTag);
                        if (e != null) {
                            depOutSet.addExtractor(e);
                            depOutSet.propagatedExtractors.put(e.tag, e);
                        }
                    }
                }
            }
        }

        for (Set depOutSet : s.dependencyTransformation.outputSets.values()) {
            for (ExtractorCombination ec : depOutSet.extractorCombinations) {
                if (s.extractors.containsKey(ec.innerExtractorTag)
                        && s.extractors.containsKey(ec.outerExtractorTag)) {
                    s.extractorCombinations.add(ec);
                }
            }
        }

//        for (Set out : s.dependencyTransformation.outputSets.values()) {
//            if (s.tag.equals(out.tag)) {
//                s.attributes.addAll(out.attributes);
//
//                for (Extractor e : out.extractors.values()) {
//                    if (s.extractors.get(e.tag) == null) {
//                        s.extractors.put(e.tag, e);
//                        s.propagatedExtractors.put(e.tag, e);
//                    }
//                }
//
//                for (Attribute a : out.getAttributes()) {
//                    for (Set sOut : s.transformation.outputSets.values()) {
//                        if (sOut.hasAttribute(a.name)
//                                && !sOut.extractors.containsKey(a.extractorTag)) {
//                            Extractor e = s.getExtractor(a.extractorTag);
//                            if (e != null) {
//                                sOut.addExtractor(e);
//                                sOut.propagatedExtractors.put(e.tag, e);
//                            }
//                        }
//                    }
//                }
//
//                for (ExtractorCombination ec : out.extractorCombinations) {
//                    if (s.extractors.containsKey(ec.innerExtractorTag)
//                            && s.extractors.containsKey(ec.outerExtractorTag)) {
//                        s.extractorCombinations.add(ec);
//                    }
//                }
//            }
//        }
    }

    private static void updateOutputSet(Set set) {
//        we have changed the order for updating dataset specification
        for (Set setOut : set.transformation.outputSets.values()) {
            for (Attribute att : setOut.getAttributes()) {
                for (Set setIn : set.transformation.inputSets.values()) {
                    if (setIn.hasAttribute(att.name)
                            && att.extractorTag != null
                            && !setOut.extractors.containsKey(att.extractorTag)) {
                        Extractor e = set.getExtractor(att.extractorTag);
                        setOut.addExtractor(e);
                        setOut.propagatedExtractors.put(e.tag, e);
                    }
                }
            }
        }

        for (Set in : set.transformation.inputSets.values()) {
            for (ExtractorCombination ec : in.extractorCombinations) {
                if (set.extractors.containsKey(ec.innerExtractorTag)
                        && set.extractors.containsKey(ec.outerExtractorTag)) {
                    set.extractorCombinations.add(ec);
                }
            }
        }
    }

    public static Transaction generationTaskTransaction(String fileName, String path, JSONObject jsonFile, DBMS dbms) {
        Transaction t = new Transaction(TransactionType.TASK, fileName, path, dbms);

        Task task = new Task();
        task.ID = Integer.parseInt((String) jsonFile.get("id"));

        Object objSubID = jsonFile.get("subid");
        if (objSubID != null && !((String) objSubID).equals("null")) {
            task.subID = Integer.parseInt((String) objSubID);
        }
        task.workspace = (String) jsonFile.get("workspace");
        //task.invocation = (String) jsonFile.get("invocation");
        task.dataflowTag = (String) jsonFile.get("dataflow");
        task.transformationTag = (String) jsonFile.get("transformation");
        task.resource = (String) jsonFile.get("resource");
        task.output = (String) jsonFile.get("output");
        task.error = (String) jsonFile.get("error");
        task.status = ((String) jsonFile.get("status")).toUpperCase();
        // Ausente em clientes antigos.
        Object execution = jsonFile.get("execution");
        task.executionTag = execution == null ? "" : (String) execution;

        JSONArray performances = (JSONArray) jsonFile.get("performances");
        if (performances != null) {
            Iterator<JSONObject> it = performances.iterator();
            while (it.hasNext()) {
                JSONObject performance = it.next();

                Performance p = new Performance();
                p.startTime = (String) performance.get("startTime");
                p.endTime = (String) performance.get("endTime");
                p.method = (String) performance.get("method");
                p.description = (String) performance.get("description");

                task.performances.add(p);
            }
        }

        JSONObject dep = (JSONObject) jsonFile.get("dependency");
        if (dep != null) {
            JSONArray tags = (JSONArray) dep.get("tags");
            if (tags != null) {
                Iterator<JSONObject> it = tags.iterator();
                while (it.hasNext()) {
                    JSONObject tag = it.next();
                    task.dependencyTags.add((String) tag.get("tag"));
                }
            }

            JSONArray ids = (JSONArray) dep.get("ids");
            if (ids != null) {
                Iterator<JSONObject> record = ids.iterator();
                while (record.hasNext()) {
                    JSONObject obj = record.next();
                    String recordValue = (String) obj.get("id");
                    task.dependencyIDs.add(recordValue.split(","));
                }
            }
        }

        JSONArray files = (JSONArray) jsonFile.get("files");
        if (files != null) {
            Iterator<JSONObject> it = files.iterator();
            while (it.hasNext()) {
                JSONObject fileJSON = it.next();

                dfFile f = new dfFile();
                f.path = (String) fileJSON.get("path");
                f.name = (String) fileJSON.get("name");
                task.files.add(f);
            }
        }

        JSONArray sets = (JSONArray) jsonFile.get("sets");
        if (sets != null) {
            Iterator<JSONObject> it = sets.iterator();
            while (it.hasNext()) {
                JSONObject setJSON = it.next();

                Element e = new Element();
                e.dataflowTag = task.dataflowTag;
                e.transformationTag = task.transformationTag;
                e.setTag = (String) setJSON.get("tag");

                JSONArray elements = (JSONArray) setJSON.get("elements");
                if (elements != null) {
                    for (Object element : elements.toArray()) {
                        if (element instanceof JSONArray) {
                            JSONArray values = (JSONArray) element;
                            for (Object value : values) {
                                e.values.add((String) value);
                            }
                        } else {
                            e.values.add((String) element);
                        }
                    }
                }

                task.elements.add(e);
            }
        }

        t.addObject(task);

        return t;
    }

}

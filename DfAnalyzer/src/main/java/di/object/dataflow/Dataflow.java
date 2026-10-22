package di.object.dataflow;

import di.enumeration.dataflow.DataflowType;
import di.enumeration.dataflow.SetType;
import java.util.ArrayList;
import di.dataflow.object.DataflowObject;

/**
 *
 * @author vitor
 */
public class Dataflow extends DataflowObject {

    public Integer version;
    public String origin;
    public String generator;
    public ArrayList<Transformation> transformations = new ArrayList<>();
    public ArrayList<Set> sets = new ArrayList<>();

    protected Dataflow(DataflowType type) {
        super(type);
    }

    public Dataflow() {
        this(DataflowType.DATAFLOW);
    }

    public ArrayList<Set> getSetsFromTransformation(String transformationTag) {
        ArrayList<Set> result = new ArrayList<>();

        for (Set s : sets) {
            if (s.type == SetType.INPUT && s.transformation.tag.equals(transformationTag)) {
                result.add(s);
            }
        }

        for (Set s : sets) {
            if (s.type == SetType.OUTPUT && s.transformation.tag.equals(transformationTag)) {
                result.add(s);
            }
        }

        return result;
    }

    public Transformation getTransformation(String depDtTag) {
        for(Transformation dt : transformations){
            if(dt.tag.equals(depDtTag)){
                return dt;
            }
        }
        
        return null;
    }

}

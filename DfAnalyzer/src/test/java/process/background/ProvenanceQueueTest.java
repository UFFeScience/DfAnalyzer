package process.background;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import di.object.dataflow.Dataflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Uma transação inválida não pode derrubar a thread de ingestão.
 *
 * @author joao
 */
public class ProvenanceQueueTest {

    @SuppressWarnings("unchecked")
    private static List<Dataflow> inMemory(ProvenanceQueue queue) throws Exception {
        Field field = ProvenanceQueue.class.getDeclaredField("dataflowsInMemory");
        field.setAccessible(true);
        return (List<Dataflow>) field.get(queue);
    }

    private static void seed(ProvenanceQueue queue, Dataflow... dataflows) throws Exception {
        List<Dataflow> list = inMemory(queue);
        list.clear();
        list.addAll(new ArrayList<>(Arrays.asList(dataflows)));
    }

    private static Dataflow dataflowTagged(String tag) {
        Dataflow dataflow = new Dataflow();
        dataflow.dataflowTag = tag;
        return dataflow;
    }

    @Test
    public void lookupSurvivesANullEntryInTheList() throws Exception {
        ProvenanceQueue queue = new ProvenanceQueue(null);
        Dataflow wanted = dataflowTagged("soma");
        // Um null no meio da lista.
        seed(queue, null, wanted);

        assertSame("o dataflow depois do null tem de ser encontrado",
                wanted, queue.getDataflow("soma"));
    }

    @Test
    public void lookupSurvivesADataflowWithoutATag() throws Exception {
        ProvenanceQueue queue = new ProvenanceQueue(null);
        Dataflow wanted = dataflowTagged("soma");
        seed(queue, dataflowTagged(null), wanted);

        assertSame(wanted, queue.getDataflow("soma"));
    }

    @Test
    public void lookupOfAnUnknownTagReturnsNullInsteadOfThrowing() throws Exception {
        ProvenanceQueue queue = new ProvenanceQueue(null);
        seed(queue, null, dataflowTagged(null), dataflowTagged("soma"));

        assertNull(queue.getDataflow("nao_existe"));
        assertNull("uma tag nula nao pode casar com nada", queue.getDataflow(null));
    }

    @Test
    public void removalSurvivesTheSameGarbage() throws Exception {
        ProvenanceQueue queue = new ProvenanceQueue(null);
        Dataflow doomed = dataflowTagged("soma");
        seed(queue, null, dataflowTagged(null), doomed);

        queue.removeDataflow("soma");

        assertTrue("o dataflow removido nao pode continuar acessivel",
                queue.getDataflow("soma") == null);
        assertEquals("apenas a entrada pedida sai da lista", 2, inMemory(queue).size());
    }

    @Test
    public void removingSomethingAbsentIsANoOp() throws Exception {
        ProvenanceQueue queue = new ProvenanceQueue(null);
        seed(queue, null, dataflowTagged("soma"));

        queue.removeDataflow("outro");

        assertEquals(2, inMemory(queue).size());
    }

    @Test
    public void ingestionErrorCountersStartClean() {
        ProvenanceQueue queue = new ProvenanceQueue(null);

        assertEquals(0, queue.ingestionErrors);
        assertNull(queue.lastIngestionError);
        assertEquals(0, queue.transactionsProcessed);
    }
}

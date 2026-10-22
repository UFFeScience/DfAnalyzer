package rest.server.v2;

import java.util.List;

/**
 * Vazão de ingestão de um dataflow.
 *
 * @author joao
 */
public class IngestionThroughput {

    /** 24 faixas de uma hora, da mais antiga à atual. */
    private final List<Integer> hourlyTaskCounts;
    private final double tasksPerMinute;
    /** Variação da 1ª metade da janela para a 2ª; null sem base de comparação. */
    private final Double changePercent;

    public IngestionThroughput(List<Integer> hourlyTaskCounts, double tasksPerMinute,
            Double changePercent) {
        this.hourlyTaskCounts = hourlyTaskCounts;
        this.tasksPerMinute = tasksPerMinute;
        this.changePercent = changePercent;
    }

    public List<Integer> getHourlyTaskCounts() { return hourlyTaskCounts; }
    public double getTasksPerMinute() { return tasksPerMinute; }
    public Double getChangePercent() { return changePercent; }
}

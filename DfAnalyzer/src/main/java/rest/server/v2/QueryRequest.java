package rest.server.v2;

import java.util.List;

/**
 * Corpo de POST /api/v2/dataflows/{id}/query.
 *
 * @author joao
 */
public class QueryRequest {

    private Integer version;
    private Integer execution;
    private String mapping;
    private List<String> sources;
    private List<String> targets;
    private List<String> includes;
    private List<String> excludes;
    private List<String> projections;
    private List<String> selections;
    private Integer page;
    private Integer size;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    /** Filtra a consulta a uma execução; nula = todas. */
    public Integer getExecution() { return execution; }
    public void setExecution(Integer execution) { this.execution = execution; }

    public String getMapping() { return mapping; }
    public void setMapping(String mapping) { this.mapping = mapping; }

    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }

    public List<String> getTargets() { return targets; }
    public void setTargets(List<String> targets) { this.targets = targets; }

    public List<String> getIncludes() { return includes; }
    public void setIncludes(List<String> includes) { this.includes = includes; }

    public List<String> getExcludes() { return excludes; }
    public void setExcludes(List<String> excludes) { this.excludes = excludes; }

    public List<String> getProjections() { return projections; }
    public void setProjections(List<String> projections) { this.projections = projections; }

    public List<String> getSelections() { return selections; }
    public void setSelections(List<String> selections) { this.selections = selections; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}

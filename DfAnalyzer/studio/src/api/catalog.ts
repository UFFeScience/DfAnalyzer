import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend } from './client';
import type { components } from './schema';

/** Hooks dos endpoints de catálogo da v2. */
export type DataflowSummary = components['schemas']['DataflowSummary'];
export type DataflowStatus = components['schemas']['DataflowStatus'];
export type DataflowSummaryPage = components['schemas']['DataflowSummaryPage'];
export type Overview = components['schemas']['Overview'];

export const catalogKeys = {
  dataflows: (page: number, size: number) => ['v2', 'dataflows', page, size] as const,
  overview: () => ['v2', 'overview'] as const,
};

export function useDataflowSummaries(page = 0, size = 25) {
  return useQuery({
    queryKey: catalogKeys.dataflows(page, size),
    queryFn: ({ signal }) =>
      apiGet<DataflowSummaryPage>(`/api/v2/dataflows?page=${page}&size=${size}`, signal),
  });
}

export function useOverview() {
  return useQuery({
    queryKey: catalogKeys.overview(),
    queryFn: ({ signal }) => apiGet<Overview>('/api/v2/overview', signal),
    // A fila de ingestão muda sozinha.
    refetchInterval: 15_000,
  });
}

export type PendingTransaction = components['schemas']['PendingTransaction'];

/** Conteúdo da fila de ingestão, pedido só com a lista aberta. */
export function usePendingTransactions(enabled: boolean) {
  return useQuery({
    queryKey: ['v2', 'ingestion', 'pending'] as const,
    queryFn: ({ signal }) => apiGet<PendingTransaction[]>('/api/v2/ingestion/pending', signal),
    enabled,
    refetchInterval: enabled ? 5_000 : false,
  });
}

export type GraphResponse = components['schemas']['GraphResponse'];
export type GraphNode = components['schemas']['GraphNode'];
export type GraphEdge = components['schemas']['GraphEdge'];
export type AttributeInfo = components['schemas']['AttributeInfo'];
export type LineageResponse = components['schemas']['LineageResponse'];

export type GraphView = 'transformation' | 'dataset';

export const graphKeys = {
  graph: (id: number, view: GraphView) => ['v2', 'graph', id, view] as const,
  lineage: (id: number, view: GraphView, node: string) =>
    ['v2', 'lineage', id, view, node] as const,
};

export function useGraph(dataflowId: number | null, view: GraphView) {
  return useQuery({
    queryKey: graphKeys.graph(dataflowId ?? -1, view),
    queryFn: ({ signal }) =>
      apiGet<GraphResponse>(`/api/v2/dataflows/${dataflowId}/graph?view=${view}`, signal),
    enabled: dataflowId !== null,
    // Proveniência prospectiva só muda quando o dataflow é salvo.
    staleTime: 5 * 60_000,
  });
}

export function useLineage(dataflowId: number | null, view: GraphView, nodeId: string | null) {
  return useQuery({
    queryKey: graphKeys.lineage(dataflowId ?? -1, view, nodeId ?? ''),
    queryFn: ({ signal }) =>
      apiGet<LineageResponse>(
        `/api/v2/dataflows/${dataflowId}/lineage?node=${encodeURIComponent(nodeId!)}&view=${view}`,
        signal,
      ),
    enabled: dataflowId !== null && nodeId !== null,
    staleTime: 5 * 60_000,
  });
}


export type Annotation = components['schemas']['Annotation'];
export type AnnotationRequest = components['schemas']['AnnotationRequest'];

export const annotationKeys = {
  byDataflow: (id: number) => ['v2', 'annotations', id] as const,
};

export function useAnnotations(dataflowId: number | null) {
  return useQuery({
    queryKey: annotationKeys.byDataflow(dataflowId ?? -1),
    queryFn: ({ signal }) =>
      apiGet<Annotation[]>(`/api/v2/dataflows/${dataflowId}/annotations`, signal),
    enabled: dataflowId !== null,
  });
}

export function useCreateAnnotation(dataflowId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: AnnotationRequest) =>
      apiSend<Annotation>('POST', `/api/v2/dataflows/${dataflowId}/annotations`, request),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: annotationKeys.byDataflow(dataflowId) });
    },
  });
}

export function useDeleteAnnotation(dataflowId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (annotationId: number) =>
      apiSend<void>('DELETE', `/api/v2/annotations/${annotationId}`),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: annotationKeys.byDataflow(dataflowId) });
    },
  });
}


export type QueryRequest = components['schemas']['QueryRequest'];
export type QueryResult = components['schemas']['QueryResult'];
export type QueryColumn = components['schemas']['QueryColumn'];

export function useQueryRun(dataflowId: number) {
  return useMutation({
    mutationFn: (request: QueryRequest) =>
      apiSend<QueryResult>('POST', `/api/v2/dataflows/${dataflowId}/query`, request),
  });
}

/** Consulta v2 como leitura em cache, refeita quando os parâmetros mudam. */
export function useDatasetSnapshot(
  dataflowId: number | null,
  request: QueryRequest | null,
) {
  return useQuery({
    queryKey: ['v2', 'query', dataflowId ?? -1, request] as const,
    queryFn: () => apiSend<QueryResult>('POST', `/api/v2/dataflows/${dataflowId}/query`, request),
    enabled: dataflowId !== null && request !== null,
  });
}

export function provJsonUrl(dataflowId: number): string {
  return `/api/v2/dataflows/${dataflowId}/prov.json`;
}


export type VersionSummary = components['schemas']['VersionSummary'];
export type ExecutionSummary = components['schemas']['ExecutionSummary'];
export type ExecutionTimeline = components['schemas']['ExecutionTimeline'];
export type TimelineRow = components['schemas']['TimelineRow'];
export type TaskAnomaly = components['schemas']['TaskAnomaly'];
export type IngestionHealth = components['schemas']['IngestionHealth'];
export type IngestionAlert = components['schemas']['IngestionAlert'];
export type IngestionThroughput = components['schemas']['IngestionThroughput'];

export const analyticsKeys = {
  versions: (id: number) => ['v2', 'versions', id] as const,
  executions: (id: number, version: number) => ['v2', 'executions', id, version] as const,
  timeline: (id: number, execId: number) => ['v2', 'timeline', id, execId] as const,
  ingestionHealth: (id: number) => ['v2', 'ingestion-health', id] as const,
  ingestionThroughput: (id: number) => ['v2', 'ingestion-throughput', id] as const,
};

export function useVersions(dataflowId: number | null) {
  return useQuery({
    queryKey: analyticsKeys.versions(dataflowId ?? -1),
    queryFn: ({ signal }) =>
      apiGet<VersionSummary[]>(`/api/v2/dataflows/${dataflowId}/versions`, signal),
    enabled: dataflowId !== null,
  });
}

/** `live` liga o polling, porque execuções e tasks mudam enquanto o pipeline roda. */
export function useExecutions(
  dataflowId: number | null,
  version: number | null,
  opts?: { live?: boolean },
) {
  return useQuery({
    queryKey: analyticsKeys.executions(dataflowId ?? -1, version ?? -1),
    queryFn: ({ signal }) =>
      apiGet<ExecutionSummary[]>(
        `/api/v2/dataflows/${dataflowId}/versions/${version}/executions`,
        signal,
      ),
    enabled: dataflowId !== null && version !== null,
    refetchInterval: opts?.live ? 10_000 : undefined,
  });
}

export function useExecutionTimeline(
  dataflowId: number | null,
  execId: number | null,
  opts?: { live?: boolean },
) {
  return useQuery({
    queryKey: analyticsKeys.timeline(dataflowId ?? -1, execId ?? -1),
    queryFn: ({ signal }) =>
      apiGet<ExecutionTimeline>(
        `/api/v2/dataflows/${dataflowId}/executions/${execId}/timeline`,
        signal,
      ),
    enabled: dataflowId !== null && execId !== null,
    refetchInterval: opts?.live ? 6_000 : undefined,
  });
}

export function useIngestionHealth(dataflowId: number | null) {
  return useQuery({
    queryKey: analyticsKeys.ingestionHealth(dataflowId ?? -1),
    queryFn: ({ signal }) =>
      apiGet<IngestionHealth>(`/api/v2/dataflows/${dataflowId}/ingestion-health`, signal),
    enabled: dataflowId !== null,
    refetchInterval: 15_000,
  });
}

export function useIngestionThroughput(dataflowId: number | null) {
  return useQuery({
    queryKey: analyticsKeys.ingestionThroughput(dataflowId ?? -1),
    queryFn: ({ signal }) =>
      apiGet<IngestionThroughput>(`/api/v2/dataflows/${dataflowId}/ingestion-throughput`, signal),
    enabled: dataflowId !== null,
    refetchInterval: 15_000,
  });
}

import { Alert, AlertTitle, Box, Skeleton, Stack, useTheme } from '@mui/material';
import { useMemo, useState } from 'react';
import type { ChangeEvent } from 'react';
import {
  provJsonUrl,
  useExecutions,
  useGraph,
  useQueryRun,
  useVersions,
  type AttributeInfo,
  type QueryRequest,
} from '../api/catalog';
import { Chip, Mono, StudioCard } from '../components/primitives';
import { CheckIcon, ExportIcon, SearchIcon } from '../layout/icons';
import { SectionSwitch, type DataflowSection } from '../layout/SectionSwitch';
import { downloadCsv, toCsv } from '../query/csv';
import { downloadNotebook, queryToNotebook } from '../query/notebook';
import { conditionsToSelections, operatorsFor, type Condition } from '../query/operators';
import { findDerivationPath } from '../query/pathfinding';
import {
  CheckItem, MiniPill, Section, SidePanel, SqlBlock, UnderlineTabs,
} from '../query/panels';
import { ResultTable } from '../query/ResultTable';

type MainTab = 'sql' | 'path';

interface Column {
  qualified: string;
  dataset: string;
  attribute: string;
  type: AttributeInfo['type'];
}

export function QueryPage({
  dataflowId,
  dataflowTag,
  initialDataset = null,
  onBack,
  section,
  onNavigateSection,
}: {
  dataflowId: number;
  dataflowTag: string;
  initialDataset?: string | null;
  onBack: () => void;
  section: DataflowSection;
  onNavigateSection: (section: DataflowSection) => void;
}) {
  const { studio } = useTheme();
  const graphDatasets = useGraph(dataflowId, 'dataset');
  const graphFlow = useGraph(dataflowId, 'transformation');
  const run = useQueryRun(dataflowId);

  // É a versão que o backend usa quando a consulta não informa `version`.
  const versions = useVersions(dataflowId);
  const latestVersion = versions.data?.[0]?.version ?? null;
  const executions = useExecutions(dataflowId, latestVersion);

  const [chosen, setChosen] = useState<string[]>(initialDataset ? [initialDataset] : []);
  const [picked, setPicked] = useState<string[]>([]);
  const [conditions, setConditions] = useState<Condition[]>([]);
  const [limit, setLimit] = useState(100);
  const [execution, setExecution] = useState<number | null>(null);
  const [tab, setTab] = useState<MainTab>('sql');
  const [filter, setFilter] = useState('');

  const datasets = useMemo(
    () => (graphDatasets.data?.nodes ?? []).filter((node) => node.kind === 'DATASET'),
    [graphDatasets.data],
  );

  const columns: Column[] = useMemo(() => {
    const scope = chosen.length > 0
      ? datasets.filter((d) => chosen.includes(d.tag))
      : datasets;
    return scope.flatMap((dataset) =>
      (dataset.attributes ?? []).map((attribute) => ({
        qualified: `${dataset.tag}.${attribute.name}`,
        dataset: dataset.tag,
        attribute: attribute.name,
        type: attribute.type,
      })),
    );
  }, [datasets, chosen]);

  const shownColumns = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    return needle
      ? columns.filter((c) => c.qualified.toLowerCase().includes(needle))
      : columns;
  }, [columns, filter]);

  const request: QueryRequest = useMemo(() => {
    const sorted = [...chosen];
    return {
      mapping: 'LOGICAL',
      sources: sorted.length > 1 ? [sorted[0]] : sorted,
      targets: sorted.length > 1 ? [sorted[sorted.length - 1]] : [],
      projections: picked,
      selections: conditionsToSelections(conditions),
      execution: execution ?? undefined,
      page: 0,
      size: limit,
    };
  }, [chosen, picked, conditions, execution, limit]);

  const path = useMemo(() => {
    if (!graphFlow.data || chosen.length < 2) return null;
    return findDerivationPath(graphFlow.data, chosen[0], chosen[chosen.length - 1]);
  }, [graphFlow.data, chosen]);

  const removeDataset = (tag: string) => {
    const ofTag = (column: string) => column.startsWith(`${tag}.`);
    setChosen(chosen.filter((t) => t !== tag));
    setPicked(picked.filter((column) => !ofTag(column)));
    setConditions(conditions.filter((condition) => !ofTag(condition.column)));
  };

  const loading = graphDatasets.isPending || graphFlow.isPending;

  return (
    <>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ flex: 'none', flexWrap: 'wrap', rowGap: 1 }}>
        <Box
          component="button" type="button" onClick={onBack}
          sx={{
            border: 0, bgcolor: 'transparent', p: 0, color: studio.muted,
            cursor: 'pointer', fontSize: 13, fontFamily: 'inherit',
            '&:hover': { color: studio.ink },
          }}
        >
          Dataflows
        </Box>
        <Box sx={{ color: studio.muted, fontSize: 13 }}>/</Box>
        <Mono sx={{ fontSize: 13, fontWeight: 600 }}>{dataflowTag}</Mono>
        <Box sx={{ color: studio.muted, fontSize: 13 }}>/</Box>
        <Box sx={{ fontSize: 13, color: studio.muted }}>Consulta</Box>
        <Box sx={{ flexGrow: 1 }} />
        <SectionSwitch section={section} onNavigate={onNavigateSection} />
        <GhostButton
          icon={<ExportIcon size={14} />}
          onClick={() => window.open(provJsonUrl(dataflowId), '_blank')}
          title="Proveniência prospectiva no formato W3C PROV-JSON"
        >
          PROV-JSON
        </GhostButton>
      </Stack>

      {graphDatasets.isError && (
        <Alert severity="error">
          <AlertTitle>Não foi possível carregar o catálogo</AlertTitle>
          {graphDatasets.error instanceof Error ? graphDatasets.error.message : 'Erro desconhecido.'}
        </Alert>
      )}

      {loading && <Skeleton variant="rounded" height={340} />}

      {!loading && !graphDatasets.isError && (
        <Box
          sx={{
            flexGrow: 1, minHeight: 0, display: 'flex',
            flexDirection: { xs: 'column', lg: 'row' }, gap: 2,
          }}
        >
          <SidePanel>
            <Section label="DATASETS">
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, mb: chosen.length ? 1 : 0 }}>
                {chosen.map((tag) => (
                  <Box
                    key={tag} component="button" type="button"
                    onClick={() => removeDataset(tag)}
                    aria-label={`Remover ${tag}`}
                    sx={{
                      display: 'inline-flex', alignItems: 'center', gap: 0.625, height: 22,
                      px: 1, borderRadius: '6px', fontSize: 11, fontWeight: 500,
                      fontFamily: 'inherit', cursor: 'pointer',
                      color: studio.aqua, border: `1px solid ${studio.aqua}`,
                      bgcolor: studio.aquaDim,
                      '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
                    }}
                  >
                    {tag} ×
                  </Box>
                ))}
              </Box>
              <MiniPill
                as="select"
                value=""
                ariaLabel="Adicionar dataset"
                grow={1}
                onChange={(tag) => {
                  if (tag && !chosen.includes(tag)) setChosen([...chosen, tag]);
                }}
              >
                <option value="">+ Adicionar do grafo</option>
                {datasets
                  .filter((d) => !chosen.includes(d.tag))
                  .map((d) => (
                    <option key={d.tag} value={d.tag}>{d.tag}</option>
                  ))}
              </MiniPill>
            </Section>

            <Section label={`PROJEÇÃO · ${picked.length} DE ${columns.length}`}>
              <Box
                sx={{
                  height: 26, display: 'flex', alignItems: 'center', gap: 0.75, px: 1, mb: 1,
                  borderRadius: '7px', border: `1px solid ${studio.border}`,
                  bgcolor: studio.page, color: studio.muted,
                  '&:focus-within': { borderColor: studio.blue },
                }}
              >
                <SearchIcon size={13} />
                <Box
                  component="input" value={filter}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => setFilter(e.target.value)}
                  placeholder="Filtrar atributos" aria-label="Filtrar atributos"
                  sx={{
                    flexGrow: 1, minWidth: 0, border: 0, outline: 'none',
                    bgcolor: 'transparent', color: studio.ink,
                    fontFamily: 'inherit', fontSize: 11.5,
                    '&::placeholder': { color: studio.muted },
                  }}
                />
              </Box>
              <Box sx={{ maxHeight: 210, overflowY: 'auto' }}>
                {shownColumns.length === 0 && (
                  <Box sx={{ fontSize: 11.5, color: studio.muted }}>
                    {chosen.length === 0
                      ? 'Escolha um dataset acima.'
                      : 'Nenhum atributo bate com o filtro.'}
                  </Box>
                )}
                {shownColumns.map((column) => (
                  <CheckItem
                    key={column.qualified}
                    label={column.attribute}
                    hint={`${column.qualified} · ${column.type ?? '—'}`}
                    checked={picked.includes(column.qualified)}
                    onToggle={() =>
                      setPicked(
                        picked.includes(column.qualified)
                          ? picked.filter((p) => p !== column.qualified)
                          : [...picked, column.qualified],
                      )
                    }
                  />
                ))}
              </Box>
            </Section>

            <Section label="FILTROS">
              {conditions.map((condition, index) => {
                const type = columns.find((c) => c.qualified === condition.column)?.type ?? 'TEXT';
                const operators = operatorsFor(type);
                return (
                  <Stack key={index} direction="row" spacing={0.625} alignItems="center" sx={{ mb: 0.875 }}>
                    <MiniPill
                      as="select" grow={2} ariaLabel="Atributo" value={condition.column}
                      onChange={(next) => {
                        const t = columns.find((c) => c.qualified === next)?.type ?? 'TEXT';
                        update(index, { column: next, operator: operatorsFor(t)[0] });
                      }}
                    >
                      {columns.map((c) => (
                        <option key={c.qualified} value={c.qualified}>{c.attribute}</option>
                      ))}
                    </MiniPill>
                    <MiniPill
                      as="select" grow={1} ariaLabel="Operador" value={condition.operator.sql}
                      onChange={(next) =>
                        update(index, { operator: operators.find((o) => o.sql === next)! })
                      }
                    >
                      {operators.map((o) => (
                        <option key={o.sql} value={o.sql}>{o.label}</option>
                      ))}
                    </MiniPill>
                    {condition.operator.operands >= 1 && (
                      <MiniPill as="input" grow={1} ariaLabel="Valor" value={condition.value}
                        onChange={(next) => update(index, { value: next })} />
                    )}
                    {condition.operator.operands === 2 && (
                      <MiniPill as="input" grow={1} ariaLabel="Segundo valor" value={condition.value2}
                        onChange={(next) => update(index, { value2: next })} />
                    )}
                    <Box
                      component="button" type="button"
                      onClick={() => setConditions(conditions.filter((_, i) => i !== index))}
                      aria-label="Remover condição"
                      sx={{
                        flex: 'none', border: 0, bgcolor: 'transparent', color: studio.muted,
                        cursor: 'pointer', fontSize: 14, lineHeight: 1, px: 0.25,
                        '&:hover': { color: studio.crit },
                      }}
                    >
                      ×
                    </Box>
                  </Stack>
                );
              })}
              <GhostButton
                onClick={() => {
                  const first = columns[0];
                  if (!first) return;
                  setConditions([
                    ...conditions,
                    { column: first.qualified, operator: operatorsFor(first.type)[0], value: '', value2: '' },
                  ]);
                }}
                disabled={columns.length === 0}
              >
                + Condição
              </GhostButton>
              <Box sx={{ mt: 1, fontSize: 11, color: studio.muted, lineHeight: 1.5 }}>
                Operadores derivados do tipo do atributo. Os valores são validados contra
                o catálogo no servidor — nunca concatenados na SQL.
              </Box>
            </Section>

            <Section label="EXECUÇÃO E LIMITE">
              <Stack direction="row" spacing={0.625} sx={{ mb: 0 }}>
                <MiniPill
                  as="select" grow={2} ariaLabel="Filtrar por execução"
                  value={execution === null ? '' : String(execution)}
                  onChange={(next) => setExecution(next === '' ? null : Number(next))}
                >
                  <option value="">Todas as execuções</option>
                  {(executions.data ?? []).map((e) => (
                    <option key={e.id} value={e.id}>#{e.id} · {e.tag}</option>
                  ))}
                </MiniPill>
                <MiniPill
                  as="select" grow={1} ariaLabel="Limite de linhas" value={String(limit)}
                  onChange={(next) => setLimit(Number(next))}
                >
                  {[50, 100, 500, 1000].map((n) => (
                    <option key={n} value={n}>{n} linhas</option>
                  ))}
                </MiniPill>
              </Stack>
              {execution !== null && (
                <Box sx={{ mt: 0.875, fontSize: 11, color: studio.muted, lineHeight: 1.5 }}>
                  Restrito à execução escolhida — o filtro entra como mais uma condição,
                  a partir dos ids de task dessa execução no servidor.
                </Box>
              )}
            </Section>
          </SidePanel>

          <Stack spacing={2} sx={{ flexGrow: 1, minWidth: 0, minHeight: 0 }}>
            <StudioCard sx={{ flex: 'none', overflow: 'hidden' }}>
              <Stack
                direction="row" alignItems="center" spacing={1}
                sx={{ px: 1.5, borderBottom: `1px solid ${studio.border}` }}
              >
                <UnderlineTabs
                  ariaLabel="Visualização da consulta"
                  value={tab}
                  onChange={setTab}
                  options={[
                    { value: 'sql', label: 'SQL gerada' },
                    { value: 'path', label: 'Caminho no grafo' },
                  ]}
                />
                <Box sx={{ flexGrow: 1 }} />
                <PrimaryButton
                  onClick={() => run.mutate(request)}
                  disabled={picked.length === 0 || run.isPending}
                >
                  {run.isPending ? 'Executando…' : 'Executar'}
                </PrimaryButton>
              </Stack>

              {tab === 'sql' ? (
                run.data ? (
                  <SqlBlock sql={run.data.sql} />
                ) : (
                  <Box sx={{ px: 1.875, py: 2.25, fontSize: 12, color: studio.muted, lineHeight: 1.6 }}>
                    {picked.length === 0
                      ? 'Escolha um dataset e marque os atributos que quer ver. A SQL aparece aqui.'
                      : `${picked.length} ${picked.length === 1 ? 'coluna' : 'colunas'} · ` +
                        `${request.selections?.length ?? 0} ${(request.selections?.length ?? 0) === 1 ? 'filtro' : 'filtros'}` +
                        ' · execute para ver a SQL gerada.'}
                  </Box>
                )
              ) : (
                <DerivationView path={path} chosen={chosen} />
              )}
            </StudioCard>

            {run.isError && (
              <Alert severity="error">
                <AlertTitle>Consulta rejeitada</AlertTitle>
                {run.error instanceof Error ? run.error.message : 'Erro desconhecido.'}
              </Alert>
            )}

            {run.data && (
              <StudioCard sx={{ flexGrow: 1, overflow: 'hidden' }}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }} spacing={1}
                  alignItems={{ sm: 'center' }}
                  sx={{ px: 2, py: 1.5, borderBottom: `1px solid ${studio.border}`, flex: 'none' }}
                >
                  <Box component="h2" sx={{ m: 0, fontSize: 13, fontWeight: 600 }}>Resultado</Box>
                  <Chip tone="good" icon={<CheckIcon size={13} />}>colunas validadas</Chip>
                  <Box sx={{ flexGrow: 1 }} />
                  <Box sx={{ fontSize: 12, color: studio.muted, fontVariantNumeric: 'tabular-nums' }}>
                    {run.data.rows.length}
                    {run.data.hasMore ? `+ de mais` : ''} linhas · {run.data.elapsedMs} ms
                  </Box>
                  <GhostButton
                    icon={<ExportIcon size={14} />}
                    onClick={() => downloadCsv(`${dataflowTag}.csv`, toCsv(run.data))}
                  >
                    CSV
                  </GhostButton>
                  <GhostButton
                    icon={<ExportIcon size={14} />}
                    title="Notebook Jupyter que refaz esta consulta pela API, sem o limite de linhas da tela"
                    onClick={() =>
                      downloadNotebook(
                        `${dataflowTag}-consulta.ipynb`,
                        queryToNotebook({
                          baseUrl: window.location.origin,
                          dataflowId,
                          dataflowTag,
                          // A consulta que produziu este resultado, não a montada agora.
                          request: run.variables ?? request,
                          sql: run.data.sql,
                          exportedAt: new Date().toLocaleString('pt-BR'),
                        }),
                      )
                    }
                  >
                    Notebook
                  </GhostButton>
                </Stack>

                {run.data.rows.length === 0 ? (
                  <Box sx={{ p: 5, textAlign: 'center' }}>
                    <Box sx={{ fontSize: 15, fontWeight: 600, mb: 0.75 }}>Nenhuma linha</Box>
                    <Box sx={{ fontSize: 12.5, color: studio.muted }}>
                      A consulta é válida, mas não encontrou dados. Confira os filtros.
                    </Box>
                  </Box>
                ) : (
                  <ResultTable result={run.data} />
                )}
              </StudioCard>
            )}
          </Stack>
        </Box>
      )}
    </>
  );

  function update(index: number, patch: Partial<Condition>) {
    setConditions(conditions.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  }
}

function DerivationView({
  path,
  chosen,
}: {
  path: ReturnType<typeof findDerivationPath>;
  chosen: string[];
}) {
  const { studio } = useTheme();

  if (chosen.length < 2) {
    return (
      <Box sx={{ px: 1.875, py: 2.25, fontSize: 12, color: studio.muted, lineHeight: 1.6 }}>
        Escolha dois datasets para ver por onde eles se ligam.
      </Box>
    );
  }
  if (!path) {
    return (
      <Box sx={{ px: 1.875, py: 2.25, fontSize: 12, color: studio.muted, lineHeight: 1.6 }}>
        Não há caminho de derivação de <Mono>{chosen[0]}</Mono> até{' '}
        <Mono>{chosen[chosen.length - 1]}</Mono> nesta versão do dataflow. Um resultado
        juntando os dois seria um produto cartesiano, não uma relação.
      </Box>
    );
  }

  return (
    <Box sx={{ px: 1.875, py: 2 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.75 }}>
        {path.steps.map((step, index) => (
          <Box key={step.id} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            {index > 0 && <Box sx={{ color: studio.muted, fontSize: 12 }}>→</Box>}
            <Chip tone={step.kind === 'TRANSFORMATION' ? 'blue' : 'aqua'}>{step.tag}</Chip>
          </Box>
        ))}
      </Box>
      <Box sx={{ mt: 1.25, fontSize: 12, color: studio.muted, lineHeight: 1.6 }}>
        Caminho de derivação entre os dois datasets · {path.transformations}{' '}
        {path.transformations === 1 ? 'transformação' : 'transformações'}
        {path.ambiguous
          ? ' · há mais de um caminho de mesmo tamanho, este é um deles'
          : ' · nenhuma dependência ambígua'}
        .
      </Box>
    </Box>
  );
}

function GhostButton({
  children, icon, onClick, disabled, title,
}: {
  children: React.ReactNode; icon?: React.ReactNode;
  onClick?: () => void; disabled?: boolean; title?: string;
}) {
  const { studio } = useTheme();
  return (
    <Box
      component="button" type="button" onClick={onClick} disabled={disabled} title={title}
      sx={{
        height: 29, px: 1.25, borderRadius: '8px', border: `1px solid ${studio.border}`,
        bgcolor: studio.elev, color: studio.ink, fontSize: 12, fontWeight: 500,
        fontFamily: 'inherit', display: 'inline-flex', alignItems: 'center', gap: 0.75,
        cursor: disabled ? 'default' : 'pointer', opacity: disabled ? 0.45 : 1,
        whiteSpace: 'nowrap',
        '&:hover:not(:disabled)': { borderColor: studio.muted },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      {icon}
      {children}
    </Box>
  );
}

function PrimaryButton({
  children, onClick, disabled,
}: {
  children: React.ReactNode; onClick: () => void; disabled?: boolean;
}) {
  const { studio } = useTheme();
  return (
    <Box
      component="button" type="button" onClick={onClick} disabled={disabled}
      sx={{
        height: 29, px: 1.5, borderRadius: '8px', border: `1px solid ${studio.blue}`,
        bgcolor: studio.blue, color: '#fff', fontSize: 12, fontWeight: 500,
        fontFamily: 'inherit', cursor: disabled ? 'default' : 'pointer', whiteSpace: 'nowrap',
        '&:disabled': { opacity: 0.4 },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      {children}
    </Box>
  );
}

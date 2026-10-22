import { Box, CircularProgress, Stack, useTheme } from '@mui/material';
import { OriginChip, originMeaning, type MappingOrigin } from '../components/OriginChip';
import { useState } from 'react';
import type { ChangeEvent, KeyboardEvent } from 'react';
import {
  useAnnotations,
  useCreateAnnotation,
  useDeleteAnnotation,
  type Annotation,
  type GraphNode,
  type LineageResponse,
  type TimelineRow,
} from '../api/catalog';
import { Chip, Segmented } from '../components/primitives';
import { MONO } from '../theme/tokens';

type Tab = 'attributes' | 'lineage' | 'notes' | 'raw';

export function NodeInspector({
  node,
  lineage,
  dataflowId,
  mapping,
  onClose,
  onFocusNode,
  stats,
  onQuery,
  onOpenData,
}: {
  node: GraphNode;
  lineage: LineageResponse | null;
  dataflowId: number;
  /** `undefined` enquanto as versões carregam ou se falharam; a linha então não aparece. */
  mapping?: { origin: MappingOrigin; generator: string | null; version: number };
  onClose: () => void;
  onFocusNode: (nodeId: string) => void;
  /** Ausente sem execução ou quando o nó é um dataset. */
  stats?: { executionId: number; row: TimelineRow | null };
  onQuery?: () => void;
  onOpenData?: () => void;
}) {
  const { studio } = useTheme();
  const [tab, setTab] = useState<Tab>('attributes');
  const isTransformation = node.kind === 'TRANSFORMATION';

  return (
    <Box
      sx={{
        width: { xs: '100%', lg: 340 },
        flex: 'none',
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        borderLeft: { lg: `1px solid ${studio.border}` },
        borderTop: { xs: `1px solid ${studio.border}`, lg: 'none' },
        bgcolor: studio.surface,
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{ px: 2, py: 1.5, borderBottom: `1px solid ${studio.border}`, flex: 'none' }}
      >
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Box
            sx={{
              fontFamily: MONO,
              fontSize: 13,
              fontWeight: 600,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            title={node.tag}
          >
            {node.tag}
          </Box>
          <Box sx={{ fontSize: 11, color: studio.muted, mt: 0.25 }}>
            {isTransformation ? 'Transformação · PROV Activity' : 'Dataset · PROV Entity'}
            {node.role && ` · ${roleLabel(node.role)}`}
          </Box>
          {mapping && (
            <Box sx={{ mt: 0.75 }}>
              <OriginChip origin={mapping.origin} generator={mapping.generator} />
            </Box>
          )}
        </Box>
        <Box
          component="button"
          type="button"
          onClick={onClose}
          aria-label="Fechar inspeção"
          sx={{
            flex: 'none',
            width: 26,
            height: 26,
            borderRadius: '7px',
            border: 0,
            bgcolor: 'transparent',
            color: studio.muted,
            cursor: 'pointer',
            fontSize: 16,
            lineHeight: 1,
            '&:hover': { color: studio.ink },
          }}
        >
          ×
        </Box>
      </Stack>

      {isTransformation && stats && (
        <Box sx={{ px: 2, pt: 1.5, flex: 'none' }}>
          {stats.row ? (
            <Stack direction="row" spacing={1}>
              <Metric label="TASKS" value={stats.row.taskCount.toLocaleString('pt-BR')} />
              <Metric label="DURAÇÃO" value={formatSeconds(stats.row.durationSeconds)} />
              <Metric
                label="FALHAS"
                value={stats.row.errorTaskCount.toLocaleString('pt-BR')}
                tone={stats.row.errorTaskCount > 0 ? 'crit' : undefined}
              />
            </Stack>
          ) : (
            <Box sx={{ fontSize: 11.5, color: studio.muted }}>
              Nenhuma task desta transformação na execução #{stats.executionId}.
            </Box>
          )}
          {stats.row && (
            <Box sx={{ mt: 0.75, fontSize: 11, color: studio.muted }}>execução #{stats.executionId}</Box>
          )}
        </Box>
      )}

      <Box sx={{ px: 2, py: 1.5, flex: 'none' }}>
        <Segmented
          ariaLabel="Aba de inspeção"
          value={tab}
          onChange={setTab}
          options={[
            { value: 'attributes', label: 'Atributos' },
            { value: 'lineage', label: 'Linhagem' },
            { value: 'notes', label: 'Notas' },
            { value: 'raw', label: 'JSON' },
          ]}
        />
      </Box>

      <Box sx={{ flexGrow: 1, overflowY: 'auto', px: 2, pb: 2, minHeight: 0 }}>
        {tab === 'attributes' && <AttributesTab node={node} />}
        {tab === 'lineage' && <LineageTab lineage={lineage} onFocusNode={onFocusNode} />}
        {tab === 'notes' && <NotesTab dataflowId={dataflowId} target={node.id} />}
        {tab === 'raw' && (
          <Box
            component="pre"
            sx={{
              m: 0,
              fontFamily: MONO,
              fontSize: 11,
              lineHeight: 1.5,
              color: studio.muted,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
            }}
          >
            {JSON.stringify(node, null, 2)}
          </Box>
        )}
      </Box>
      {mapping && (
        <Box
          sx={{
            px: 2,
            py: 1.25,
            flex: 'none',
            borderTop: `1px solid ${studio.border}`,
            fontSize: 11,
            color: studio.muted,
            lineHeight: 1.55,
          }}
        >
          Origem do mapeamento (versão {mapping.version}):{' '}
          {mapping.generator ? (
            <Box component="span" sx={{ fontFamily: MONO }}>{mapping.generator}</Box>
          ) : (
            originMeaning(mapping.origin)
          )}
          <br />
          Vale para a definição inteira desta versão, não para este nó em particular.
        </Box>
      )}
      {(onQuery || onOpenData) && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ p: 1.5, borderTop: `1px solid ${studio.border}`, flex: 'none', flexWrap: 'wrap' }}>
          <ActionButton onClick={() => setTab('lineage')}>Rastrear origem</ActionButton>
          {onOpenData && <ActionButton onClick={onOpenData}>Metadados completos</ActionButton>}
          {onQuery && (
            <ActionButton primary onClick={onQuery}>
              Consultar
            </ActionButton>
          )}
        </Stack>
      )}
    </Box>
  );
}

function AttributesTab({ node }: { node: GraphNode }) {
  const { studio } = useTheme();

  if (node.kind === 'TRANSFORMATION') {
    return (
      <Empty>
        Transformações não têm atributos próprios. Os atributos ficam nos datasets
        que ela consome e produz — clique numa aresta vizinha para vê-los.
      </Empty>
    );
  }
  if (!node.attributes || node.attributes.length === 0) {
    return <Empty>Este dataset não declarou atributos na proveniência prospectiva.</Empty>;
  }

  return (
    <Stack spacing={0.5}>
      {node.attributes.map((attribute) => (
        <Stack
          key={attribute.name}
          direction="row"
          alignItems="center"
          spacing={1}
          sx={{ py: 0.75, borderBottom: `1px solid ${studio.border}` }}
        >
          <Box
            sx={{
              fontFamily: MONO,
              fontSize: 12,
              flexGrow: 1,
              minWidth: 0,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            title={attribute.name}
          >
            {attribute.name}
          </Box>
          <Chip tone={attribute.type === 'FILE' ? 'aqua' : 'neutral'}>
            {attribute.type ?? '—'}
          </Chip>
        </Stack>
      ))}
    </Stack>
  );
}

function LineageTab({
  lineage,
  onFocusNode,
}: {
  lineage: LineageResponse | null;
  onFocusNode: (nodeId: string) => void;
}) {
  if (!lineage) {
    return <Empty>Carregando linhagem…</Empty>;
  }
  const empty = lineage.upstream.length === 0 && lineage.downstream.length === 0;
  if (empty) {
    return <Empty>Este nó não tem vizinhos: não recebe de ninguém nem alimenta ninguém.</Empty>;
  }

  return (
    <Stack spacing={2}>
      {lineage.downstream.length > 0 && <ImpactSummary downstream={lineage.downstream} />}
      <LineageList
        title="De onde veio"
        hint="nós dos quais este descende"
        ids={lineage.upstream}
        onFocusNode={onFocusNode}
      />
      <LineageList
        title="O que isto afetou"
        hint="nós que dependem deste"
        ids={lineage.downstream}
        onFocusNode={onFocusNode}
      />
    </Stack>
  );
}

/** Resumo do que está a jusante do nó. */
function ImpactSummary({ downstream }: { downstream: string[] }) {
  const { studio } = useTheme();
  const datasets = downstream.filter((id) => id.startsWith('d:')).length;
  const transformations = downstream.filter((id) => id.startsWith('t:')).length;

  return (
    <Box
      sx={{
        p: 1.125, borderRadius: '9px', border: `1px solid ${studio.orange}`,
        bgcolor: studio.orangeDim, fontSize: 12,
      }}
    >
      <Box sx={{ fontWeight: 600, color: studio.orange }}>
        {downstream.length} nó{downstream.length === 1 ? '' : 's'} a jusante seria
        {downstream.length === 1 ? '' : 'm'} afetado{downstream.length === 1 ? '' : 's'} por um erro aqui
      </Box>
      <Box sx={{ color: studio.muted, mt: 0.25 }}>
        {datasets} dataset{datasets === 1 ? '' : 's'} · {transformations} transformaç{transformations === 1 ? 'ão' : 'ões'}
      </Box>
    </Box>
  );
}

function LineageList({
  title,
  hint,
  ids,
  onFocusNode,
}: {
  title: string;
  hint: string;
  ids: string[];
  onFocusNode: (nodeId: string) => void;
}) {
  const { studio } = useTheme();
  return (
    <Box>
      <Box sx={{ fontSize: 11.5, fontWeight: 600, letterSpacing: '.02em' }}>{title}</Box>
      <Box sx={{ fontSize: 11, color: studio.muted, mb: 0.75 }}>{hint}</Box>
      {ids.length === 0 ? (
        <Box sx={{ fontSize: 12, color: studio.muted }}>nenhum</Box>
      ) : (
        <Stack spacing={0.5}>
          {ids.map((id) => (
            <Box
              key={id}
              component="button"
              type="button"
              onClick={() => onFocusNode(id)}
              sx={{
                textAlign: 'left',
                fontFamily: MONO,
                fontSize: 11.5,
                px: 1,
                py: 0.75,
                borderRadius: '7px',
                border: `1px solid ${studio.border}`,
                bgcolor: studio.elev,
                color: studio.ink,
                cursor: 'pointer',
                '&:hover': { borderColor: studio.blue, color: studio.blue },
                '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
              }}
            >
              {id.replace(/^[td]:/, '')}
            </Box>
          ))}
        </Stack>
      )}
    </Box>
  );
}

function NotesTab({ dataflowId, target }: { dataflowId: number; target: string }) {
  const { studio } = useTheme();
  const notes = useAnnotations(dataflowId);
  const create = useCreateAnnotation(dataflowId);
  const remove = useDeleteAnnotation(dataflowId);
  const [draft, setDraft] = useState('');

  const mine = (notes.data ?? []).filter((note) => note.target === target);

  const submit = () => {
    const body = draft.trim();
    if (!body) return;
    create.mutate({ target, body }, { onSuccess: () => setDraft('') });
  };

  return (
    <Stack spacing={1.5}>
      <Box>
        <Box
          component="textarea"
          value={draft}
          onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setDraft(event.target.value)}
          onKeyDown={(event: KeyboardEvent<HTMLTextAreaElement>) => {
            if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
              event.preventDefault();
              submit();
            }
          }}
          placeholder="O que vale lembrar sobre esta etapa?"
          aria-label="Nova anotação"
          rows={3}
          sx={{
            width: '100%',
            resize: 'vertical',
            p: 1,
            borderRadius: '8px',
            border: `1px solid ${studio.border}`,
            bgcolor: studio.page,
            color: studio.ink,
            fontFamily: 'inherit',
            fontSize: 12.5,
            lineHeight: 1.5,
            '&::placeholder': { color: studio.muted },
            '&:focus': { outline: 'none', borderColor: studio.blue },
          }}
        />
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mt: 0.75 }}>
          <Box sx={{ fontSize: 10.5, color: studio.muted, flexGrow: 1 }}>
            Ctrl+Enter para salvar
          </Box>
          <Box
            component="button"
            type="button"
            onClick={submit}
            disabled={!draft.trim() || create.isPending}
            sx={{
              height: 28,
              px: 1.5,
              borderRadius: '8px',
              border: `1px solid ${studio.blue}`,
              bgcolor: studio.blue,
              color: '#fff',
              fontFamily: 'inherit',
              fontSize: 12,
              fontWeight: 500,
              cursor: 'pointer',
              '&:disabled': { opacity: 0.4, cursor: 'default' },
              '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
            }}
          >
            {create.isPending ? 'Salvando…' : 'Anotar'}
          </Box>
        </Stack>
        {create.isError && (
          <Box sx={{ mt: 0.75, fontSize: 11.5, color: studio.crit }}>
            {create.error instanceof Error ? create.error.message : 'Não foi possível salvar.'}
          </Box>
        )}
      </Box>

      {notes.isPending && (
        <Stack direction="row" spacing={1} alignItems="center">
          <CircularProgress size={13} />
          <Box sx={{ fontSize: 12, color: studio.muted }}>Carregando notas…</Box>
        </Stack>
      )}

      {notes.isError && (
        <Empty>
          {notes.error instanceof Error ? notes.error.message : 'Erro ao carregar as notas.'}
        </Empty>
      )}

      {!notes.isPending && !notes.isError && mine.length === 0 && (
        <Empty>Nenhuma anotação neste nó ainda.</Empty>
      )}

      {mine.map((note) => (
        <NoteCard key={note.id} note={note} onDelete={() => remove.mutate(note.id)} />
      ))}
    </Stack>
  );
}

function NoteCard({ note, onDelete }: { note: Annotation; onDelete: () => void }) {
  const { studio } = useTheme();
  return (
    <Box
      sx={{
        p: 1.25,
        borderRadius: '8px',
        border: `1px solid ${studio.border}`,
        bgcolor: studio.elev,
      }}
    >
      <Box sx={{ fontSize: 12.5, lineHeight: 1.55, whiteSpace: 'pre-wrap' }}>{note.body}</Box>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mt: 0.75 }}>
        <Box sx={{ fontSize: 10.5, color: studio.muted, flexGrow: 1 }}>
          {note.author ? `${note.author} · ` : ''}
          {formatWhen(note.createdAt)}
        </Box>
        <Box
          component="button"
          type="button"
          onClick={onDelete}
          aria-label="Apagar anotação"
          sx={{
            border: 0,
            bgcolor: 'transparent',
            p: 0,
            fontSize: 10.5,
            color: studio.muted,
            cursor: 'pointer',
            fontFamily: 'inherit',
            '&:hover': { color: studio.crit },
          }}
        >
          apagar
        </Box>
      </Stack>
    </Box>
  );
}

/** `yyyy-MM-dd HH:mm:ss` → `dd/MM · HH:mm`. */
function formatWhen(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(value);
  return match ? `${match[3]}/${match[2]} · ${match[4]}:${match[5]}` : value;
}

function Empty({ children }: { children: React.ReactNode }) {
  const { studio } = useTheme();
  return <Box sx={{ fontSize: 12, color: studio.muted, lineHeight: 1.55 }}>{children}</Box>;
}

function roleLabel(role: NonNullable<GraphNode['role']>): string {
  switch (role) {
    case 'SOURCE':
      return 'entrada do fluxo';
    case 'SINK':
      return 'saída do fluxo';
    default:
      return 'intermediário';
  }
}

function Metric({ label, value, tone }: { label: string; value: string; tone?: 'crit' }) {
  const { studio } = useTheme();
  return (
    <Box sx={{ flex: '1 1 0', minWidth: 0, p: 1, borderRadius: '9px', border: `1px solid ${studio.border}`, bgcolor: studio.elev }}>
      <Box sx={{ fontSize: 10, fontWeight: 600, letterSpacing: '.04em', color: studio.muted }}>{label}</Box>
      <Box sx={{ mt: 0.25, fontSize: 15, fontWeight: 600, color: tone === 'crit' ? studio.crit : studio.ink, fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Box>
    </Box>
  );
}

function ActionButton({ children, onClick, primary }: { children: React.ReactNode; onClick: () => void; primary?: boolean }) {
  const { studio } = useTheme();
  return (
    <Box
      component="button"
      type="button"
      onClick={onClick}
      sx={{
        flex: '1 1 auto',
        px: 1.25,
        height: 32,
        borderRadius: '9px',
        border: `1px solid ${primary ? studio.blue : studio.border}`,
        bgcolor: primary ? studio.blue : studio.elev,
        color: primary ? '#fff' : studio.ink,
        fontFamily: 'inherit',
        fontSize: 12,
        fontWeight: 500,
        whiteSpace: 'nowrap',
        cursor: 'pointer',
        '&:hover': { borderColor: primary ? studio.blue : studio.muted },
        '&:focus-visible': { outline: `2px solid ${studio.blue}`, outlineOffset: 2 },
      }}
    >
      {children}
    </Box>
  );
}

function formatSeconds(seconds: number): string {
  if (seconds < 60) return `${seconds.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s`;
  return `${Math.floor(seconds / 60)}m ${String(Math.round(seconds % 60)).padStart(2, '0')}s`;
}

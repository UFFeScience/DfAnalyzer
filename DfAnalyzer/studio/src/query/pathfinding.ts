import type { GraphResponse } from '../api/catalog';

/** Caminho de derivação entre dois datasets. */
export interface DerivationPath {
  steps: { id: string; tag: string; kind: 'TRANSFORMATION' | 'DATASET' }[];
  transformations: number;
  /** Há mais de um caminho mínimo entre as pontas. */
  ambiguous: boolean;
}

export function findDerivationPath(
  graph: GraphResponse,
  fromTag: string,
  toTag: string,
): DerivationPath | null {
  if (!fromTag || !toTag || fromTag === toTag) return null;

  const byId = new Map(graph.nodes.map((node) => [node.id, node]));
  const out = new Map<string, string[]>();
  for (const edge of graph.edges) {
    if (!out.has(edge.source)) out.set(edge.source, []);
    out.get(edge.source)!.push(edge.target);
  }

  // O ponto de partida pode ser o nó `d:` ou a transformação que produz o dataset.
  const starts = entryPoints(graph, fromTag, 'source');
  const goals = new Set(entryPoints(graph, toTag, 'target'));
  if (starts.length === 0 || goals.size === 0) return null;

  const distance = new Map<string, number>();
  const previous = new Map<string, string>();
  const queue: string[] = [];
  for (const start of starts) {
    distance.set(start, 0);
    queue.push(start);
  }
  for (let head = 0; head < queue.length; head++) {
    const current = queue[head];
    for (const next of out.get(current) ?? []) {
      if (distance.has(next)) continue;
      distance.set(next, distance.get(current)! + 1);
      previous.set(next, current);
      queue.push(next);
    }
  }

  let closest: string | null = null;
  let best = Infinity;
  for (const goal of goals) {
    const d = distance.get(goal);
    if (d !== undefined && d < best) {
      best = d;
      closest = goal;
    }
  }
  if (closest === null) return null;

  // Ambíguo quando mais de um predecessor está à distância best-1.
  let alternatives = 0;
  for (const edge of graph.edges) {
    if (!goals.has(edge.target)) continue;
    if (distance.get(edge.target) !== best) continue;
    if (distance.get(edge.source) === best - 1) alternatives++;
  }
  if (alternatives > 0) alternatives -= 1;

  const ids: string[] = [];
  for (let at: string | undefined = closest; at; at = previous.get(at)) {
    ids.unshift(at);
  }

  const steps = ids
    .map((id) => byId.get(id))
    .filter((node): node is NonNullable<typeof node> => node !== undefined)
    .map((node) => ({ id: node.id, tag: node.tag, kind: node.kind }));

  return {
    steps,
    transformations: steps.filter((step) => step.kind === 'TRANSFORMATION').length,
    ambiguous: alternatives > 0,
  };
}

function entryPoints(graph: GraphResponse, tag: string, role: 'source' | 'target'): string[] {
  const asNode = graph.nodes.find((node) => node.kind === 'DATASET' && node.tag === tag);
  if (asNode) return [asNode.id];

  const edges = graph.edges.filter((edge) => edge.label === tag);
  return edges.map((edge) => (role === 'source' ? edge.target : edge.source));
}

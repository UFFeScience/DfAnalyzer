import { describe, expect, it } from 'vitest';
import type { GraphEdge, GraphNode } from '../api/catalog';
import { collapseGraph, isGroup, MIN_GROUP_SIZE } from './collapse';


function tf(tag: string): GraphNode {
  return { id: `t:${tag}`, tag, kind: 'TRANSFORMATION', role: null, attributes: null, tasks: null };
}
function ds(tag: string, role: GraphNode['role'] = 'SINK'): GraphNode {
  return { id: `d:${tag}`, tag, kind: 'DATASET', role, attributes: [], tasks: null };
}
function edge(source: string, target: string, label = 'e'): GraphEdge {
  return { id: `${source}->${target}#${label}`, source, target, label };
}

const ids = (nodes: { id: string }[]) => nodes.map((n) => n.id).sort();

describe('folhas irmãs', () => {
  const parent = tf('produz');
  const leaves = ['a', 'b', 'c', 'd'].map((t) => ds(t));
  const nodes = [parent, ...leaves];
  const edges = leaves.map((leaf) => edge(parent.id, leaf.id));

  it('junta 3+ folhas do mesmo pai num grupo só', () => {
    const result = collapseGraph(nodes, edges);
    const groups = result.nodes.filter(isGroup);

    expect(groups).toHaveLength(1);
    expect(groups[0].group.memberIds.sort()).toEqual(ids(leaves));
    expect(groups[0].tag).toBe('4 saídas');
    expect(result.collapsedCount).toBe(4);
  });

  it('preserva a ligação com o pai, sem duplicar', () => {
    const result = collapseGraph(nodes, edges);
    const group = result.nodes.find(isGroup)!;

    expect(result.edges).toHaveLength(1);
    expect(result.edges[0].source).toBe(parent.id);
    expect(result.edges[0].target).toBe(group.id);
  });

  it('não agrupa abaixo do mínimo', () => {
    const poucas = leaves.slice(0, MIN_GROUP_SIZE - 1);
    const result = collapseGraph(
      [parent, ...poucas],
      poucas.map((leaf) => edge(parent.id, leaf.id)),
    );
    expect(result.nodes.filter(isGroup)).toHaveLength(0);
    expect(result.collapsedCount).toBe(0);
  });

  it('não junta folhas de pais diferentes', () => {
    const outro = tf('outro');
    const result = collapseGraph(
      [parent, outro, ...leaves],
      [
        ...leaves.slice(0, 3).map((leaf) => edge(parent.id, leaf.id)),
        edge(outro.id, leaves[3].id),
      ],
    );
    const groups = result.nodes.filter(isGroup);
    expect(groups).toHaveLength(1);
    expect(groups[0].group.memberIds).not.toContain(leaves[3].id);
  });

  it('não junta tipos diferentes sob o mesmo pai', () => {
    const misto = [ds('x'), ds('y'), tf('z1'), tf('z2'), tf('z3')];
    const result = collapseGraph(
      [parent, ...misto],
      misto.map((node) => edge(parent.id, node.id)),
    );
    const groups = result.nodes.filter(isGroup);
    // Só as 3 transformações formam grupo; os 2 datasets ficam soltos.
    expect(groups).toHaveLength(1);
    expect(groups[0].kind).toBe('TRANSFORMATION');
  });

  it('um nó com consumidor não é folha', () => {
    const consumidor = tf('consome');
    const result = collapseGraph(
      [parent, ...leaves, consumidor],
      [...edges, edge(leaves[0].id, consumidor.id)],
    );
    const groups = result.nodes.filter(isGroup);
    expect(groups[0].group.memberIds).not.toContain(leaves[0].id);
    expect(groups[0].group.memberIds).toHaveLength(3);
  });
});

describe('cadeia linear', () => {
  const chain = ['a', 'b', 'c', 'd', 'e'].map(tf);
  const nodes = chain;
  const edges = chain.slice(0, -1).map((node, i) => edge(node.id, chain[i + 1].id));

  it('junta o miolo da sequência e mantém as pontas visíveis', () => {
    const result = collapseGraph(nodes, edges);
    const groups = result.nodes.filter(isGroup);

    expect(groups).toHaveLength(1);
    expect(groups[0].tag).toBe('3 etapas em sequência');
    expect(groups[0].group.memberIds).toEqual([chain[1].id, chain[2].id, chain[3].id]);
    expect(ids(result.nodes.filter((n) => !isGroup(n)))).toEqual([chain[0].id, chain[4].id]);
  });

  it('a sequência agrupada continua ligando ponta a ponta', () => {
    const result = collapseGraph(nodes, edges);
    const group = result.nodes.find(isGroup)!;
    const pares = result.edges.map((e) => `${e.source}>${e.target}`);

    expect(pares).toContain(`${chain[0].id}>${group.id}`);
    expect(pares).toContain(`${group.id}>${chain[4].id}`);
    expect(result.edges).toHaveLength(2);
  });

  it('para a cadeia numa bifurcação — o que se ramifica não é sequência', () => {
    const ramo = tf('ramo');
    const result = collapseGraph(
      [...chain, ramo],
      [...edges, edge(chain[2].id, ramo.id)],
    );
    const groups = result.nodes.filter(isGroup);
    // C passa a ter duas saídas: nem ele nem d/e entram numa cadeia de 3+.
    const membros = groups.flatMap((g) => g.group.memberIds);
    expect(membros).not.toContain(chain[2].id);
  });
});

describe('expansão', () => {
  const parent = tf('produz');
  const leaves = ['a', 'b', 'c'].map((t) => ds(t));
  const nodes = [parent, ...leaves];
  const edges = leaves.map((leaf) => edge(parent.id, leaf.id));

  it('um grupo expandido devolve seus membros ao grafo', () => {
    const collapsed = collapseGraph(nodes, edges);
    const groupId = collapsed.nodes.find(isGroup)!.id;

    const expanded = collapseGraph(nodes, edges, new Set([groupId]));
    expect(expanded.nodes.filter(isGroup)).toHaveLength(0);
    expect(ids(expanded.nodes)).toEqual(ids(nodes));
    expect(expanded.edges).toHaveLength(3);
  });
});

describe('invariantes que valem para qualquer grafo', () => {
  const nodes: GraphNode[] = [];
  const edges: GraphEdge[] = [];
  for (let s = 0; s < 120; s++) {
    nodes.push(tf(`etapa_${s}`));
    if (s > 0) edges.push(edge(`t:etapa_${s - 1}`, `t:etapa_${s}`));
    for (let b = 0; b < 3; b++) {
      nodes.push(ds(`o_${s}_${b}`));
      edges.push(edge(`t:etapa_${s}`, `d:o_${s}_${b}`));
    }
  }

  it('nenhum nó original some nem aparece duas vezes', () => {
    const result = collapseGraph(nodes, edges);
    const presentes = new Set<string>();
    for (const node of result.nodes) {
      const originais = isGroup(node) ? node.group.memberIds : [node.id];
      for (const id of originais) {
        expect(presentes.has(id)).toBe(false);
        presentes.add(id);
      }
    }
    expect(presentes.size).toBe(nodes.length);
  });

  it('toda aresta do resultado liga nós que existem no resultado', () => {
    const result = collapseGraph(nodes, edges);
    const existentes = new Set(result.nodes.map((n) => n.id));
    for (const e of result.edges) {
      expect(existentes.has(e.source)).toBe(true);
      expect(existentes.has(e.target)).toBe(true);
    }
  });

  it('reduz o grafo de 480 para 240 nós', () => {
    const result = collapseGraph(nodes, edges);
    expect(nodes).toHaveLength(480);
    expect(result.nodes).toHaveLength(240);
  });
});

import type { GraphEdge, GraphNode } from '../api/catalog';


/** Nó de grupo: mesma forma de um GraphNode, com os membros anexados. */
export interface GroupNode extends GraphNode {
  group: {
    kind: 'LEAVES' | 'CHAIN';
    memberIds: string[];
    /** Tags dos primeiros membros, para o rótulo. */
    sampleTags: string[];
  };
}

export type CollapsibleNode = GraphNode | GroupNode;

export function isGroup(node: CollapsibleNode): node is GroupNode {
  return 'group' in node && node.group !== undefined;
}

export interface CollapseResult {
  nodes: CollapsibleNode[];
  edges: GraphEdge[];
  /** Quantos nós originais foram absorvidos por grupos. */
  collapsedCount: number;
}

/** Abaixo disto não se agrupa. */
export const MIN_GROUP_SIZE = 3;

interface Degrees {
  incoming: Map<string, string[]>;
  outgoing: Map<string, string[]>;
}

function degrees(edges: GraphEdge[]): Degrees {
  const incoming = new Map<string, string[]>();
  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source)!.push(edge.target);
    if (!incoming.has(edge.target)) incoming.set(edge.target, []);
    incoming.get(edge.target)!.push(edge.source);
  }
  return { incoming, outgoing };
}

export function collapseGraph(
  nodes: GraphNode[],
  edges: GraphEdge[],
  keepExpanded: ReadonlySet<string> = new Set(),
): CollapseResult {
  const { incoming, outgoing } = degrees(edges);
  const byId = new Map(nodes.map((node) => [node.id, node]));

  const memberOf = new Map<string, string>();
  const groups: GroupNode[] = [];

  const addGroup = (
    kind: 'LEAVES' | 'CHAIN',
    memberIds: string[],
    tagFor: (members: GraphNode[]) => string,
  ) => {
    const members = memberIds.map((id) => byId.get(id)!).filter(Boolean);
    if (members.length < MIN_GROUP_SIZE) return;

    const id = `g:${kind.toLowerCase()}:${memberIds[0]}`;
    if (keepExpanded.has(id)) return;

    groups.push({
      id,
      tag: tagFor(members),
      kind: members[0].kind,
      role: kind === 'LEAVES' ? members[0].role : null,
      attributes: null,
      tasks: null,
      group: {
        kind,
        memberIds,
        sampleTags: members.slice(0, 3).map((member) => member.tag),
      },
    });
    memberIds.forEach((memberId) => memberOf.set(memberId, id));
  };

  // Regra 1: folhas irmãs.
  const leavesByParent = new Map<string, string[]>();
  for (const node of nodes) {
    const out = outgoing.get(node.id) ?? [];
    const inc = incoming.get(node.id) ?? [];
    if (out.length === 0 && inc.length === 1) {
      const key = `${inc[0]}|${node.kind}`;
      if (!leavesByParent.has(key)) leavesByParent.set(key, []);
      leavesByParent.get(key)!.push(node.id);
    }
  }
  for (const memberIds of leavesByParent.values()) {
    addGroup('LEAVES', memberIds, (members) =>
      `${members.length} ${members[0].kind === 'DATASET' ? 'saídas' : 'transformações'}`);
  }

  // Regra 2: cadeias lineares.
  const isLinear = (id: string) =>
    (incoming.get(id) ?? []).length === 1 &&
    (outgoing.get(id) ?? []).length === 1 &&
    !memberOf.has(id);

  const visited = new Set<string>();
  for (const node of nodes) {
    if (visited.has(node.id) || !isLinear(node.id)) continue;

    // Caminha para trás até o início do trecho linear.
    let head = node.id;
    for (;;) {
      const previous = (incoming.get(head) ?? [])[0];
      if (previous && isLinear(previous) && !visited.has(previous)) {
        head = previous;
      } else {
        break;
      }
    }

    const chain: string[] = [];
    let current: string | undefined = head;
    while (current && isLinear(current) && !visited.has(current)) {
      chain.push(current);
      visited.add(current);
      current = (outgoing.get(current) ?? [])[0];
    }

    addGroup('CHAIN', chain, (members) => `${members.length} etapas em sequência`);
  }

  const resolve = (id: string) => memberOf.get(id) ?? id;

  const resultNodes: CollapsibleNode[] = [
    ...nodes.filter((node) => !memberOf.has(node.id)),
    ...groups,
  ];

  const seen = new Set<string>();
  const resultEdges: GraphEdge[] = [];
  for (const edge of edges) {
    const source = resolve(edge.source);
    const target = resolve(edge.target);
    if (source === target) continue;
    const id = `${source}->${target}#${edge.label}`;
    if (seen.has(id)) continue;
    seen.add(id);
    resultEdges.push({ id, source, target, label: edge.label });
  }

  return { nodes: resultNodes, edges: resultEdges, collapsedCount: memberOf.size };
}

/** O grupo a que um nó original pertence no resultado, ou null. */
export function groupContaining(nodes: CollapsibleNode[], nodeId: string): GroupNode | null {
  for (const node of nodes) {
    if (isGroup(node) && node.group.memberIds.includes(nodeId)) {
      return node;
    }
  }
  return null;
}

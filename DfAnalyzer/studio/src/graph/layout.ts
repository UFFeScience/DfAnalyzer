import dagre from 'dagre';
import { Position, type Edge, type Node } from '@xyflow/react';

export const NODE_WIDTH = 190;
export const NODE_HEIGHT = 56;

export function layoutGraph(nodes: Node[], edges: Edge[]): Node[] {
  const graph = new dagre.graphlib.Graph();
  graph.setDefaultEdgeLabel(() => ({}));
  graph.setGraph({
    rankdir: 'LR',
    ranksep: 90,
    nodesep: 34,
    marginx: 24,
    marginy: 24,
  });

  nodes.forEach((node) => {
    graph.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  });
  edges.forEach((edge) => {
    graph.setEdge(edge.source, edge.target);
  });

  dagre.layout(graph);

  return nodes.map((node) => {
    const placed = graph.node(node.id);
    return {
      ...node,
      // Dagre devolve o centro do nó; o React Flow posiciona pelo canto.
      position: { x: placed.x - NODE_WIDTH / 2, y: placed.y - NODE_HEIGHT / 2 },
      // Sem tamanho declarado, o fitView com culling ligada abre a tela vazia.
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      targetPosition: Position.Left,
      sourcePosition: Position.Right,
    };
  });
}

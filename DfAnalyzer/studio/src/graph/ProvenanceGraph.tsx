import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useReactFlow,
  type Edge,
  type Node,
  type NodeChange,
  type NodeMouseHandler,
  type XYPosition,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Box, useMediaQuery, useTheme } from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { GraphResponse, LineageResponse } from '../api/catalog';
import { collapseGraph, isGroup, type CollapsibleNode } from './collapse';
import { layoutGraph } from './layout';
import { ProvenanceNode, type ProvenanceNodeData } from './ProvenanceNode';

const nodeTypes = { provenance: ProvenanceNode };

/** A partir de quantos nós vale ligar a culling do viewport. */
const LARGE_GRAPH_THRESHOLD = 150;

interface Props {
  graph: GraphResponse;
  lineage: LineageResponse | null;
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  grouped: boolean;
  expandedGroups: ReadonlySet<string>;
  onToggleGroup: (groupId: string) => void;
  onCollapseStats: (stats: { visible: number; collapsed: number }) => void;
  /** Tags de transformação com task `RUNNING` agora. */
  runningTags?: ReadonlySet<string>;
  /** Ao mudar, descarta as posições arrastadas e reenquadra. */
  layoutNonce?: number;
}

export function ProvenanceGraph(props: Props) {
  return (
    <ReactFlowProvider>
      <GraphCanvas {...props} />
    </ReactFlowProvider>
  );
}

function GraphCanvas({
  graph,
  lineage,
  selectedNodeId,
  onSelectNode,
  grouped,
  expandedGroups,
  onToggleGroup,
  onCollapseStats,
  runningTags,
  layoutNonce = 0,
}: Props) {
  const { studio } = useTheme();
  const { fitView } = useReactFlow();
  // Em tela estreita o minimapa cobre os nós.
  const showMiniMap = useMediaQuery('(min-width:600px)');

  const collapsed = useMemo(() => {
    if (!grouped) {
      return { nodes: graph.nodes as CollapsibleNode[], edges: graph.edges, collapsedCount: 0 };
    }
    return collapseGraph(graph.nodes, graph.edges, expandedGroups);
  }, [graph, grouped, expandedGroups]);

  useEffect(() => {
    onCollapseStats({
      visible: collapsed.nodes.length,
      collapsed: collapsed.collapsedCount,
    });
  }, [collapsed, onCollapseStats]);

  const positioned = useMemo(() => {
    const nodes: Node[] = collapsed.nodes.map((node) => ({
      id: node.id,
      type: 'provenance',
      position: { x: 0, y: 0 },
      data: {
        node,
        selected: false,
        inLineage: false,
        dimmed: false,
        lineageSide: null,
        running: false,
      } satisfies ProvenanceNodeData,
    }));
    const edges: Edge[] = collapsed.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.label,
    }));
    return { nodes: layoutGraph(nodes, edges), edges };
  }, [collapsed]);

  const [dragPositions, setDragPositions] = useState<Record<string, XYPosition>>({});
  useEffect(() => {
    setDragPositions({});
  }, [positioned]);
  useEffect(() => {
    if (layoutNonce === 0) return;
    setDragPositions({});
    void fitView({ padding: 0.18, duration: 240, maxZoom: 1 });
  }, [layoutNonce, fitView]);

  const handleNodesChange = useCallback((changes: NodeChange[]) => {
    const moved = changes.filter(
      (change): change is Extract<NodeChange, { type: 'position' }> =>
        change.type === 'position' && change.position != null,
    );
    if (moved.length === 0) return;
    setDragPositions((prev) => {
      const next = { ...prev };
      for (const change of moved) {
        next[change.id] = change.position as XYPosition;
      }
      return next;
    });
  }, []);

  // "t:" é o prefixo de id de transformação do GraphService.
  const runningNodeIds = useMemo(
    () => new Set([...(runningTags ?? [])].map((tag) => `t:${tag}`)),
    [runningTags],
  );

  const lineageIndex = useMemo(() => {
    if (!lineage) return null;
    return {
      upstream: new Set(lineage.upstream),
      downstream: new Set(lineage.downstream),
      edges: new Set(lineage.edges),
      focus: lineage.focus,
    };
  }, [lineage]);

  const nodes = useMemo(
    () =>
      positioned.nodes.map((node) => {
        const side = !lineageIndex
          ? null
          : node.id === lineageIndex.focus
            ? ('focus' as const)
            : lineageIndex.upstream.has(node.id)
              ? ('upstream' as const)
              : lineageIndex.downstream.has(node.id)
                ? ('downstream' as const)
                : null;

        const underlying = (node.data as ProvenanceNodeData).node;
        const running = runningNodeIds.size === 0
          ? false
          : isGroup(underlying)
            ? underlying.group.memberIds.some((id) => runningNodeIds.has(id))
            : runningNodeIds.has(node.id);

        return {
          ...node,
          position: dragPositions[node.id] ?? node.position,
          data: {
            ...(node.data as ProvenanceNodeData),
            selected: node.id === selectedNodeId,
            inLineage: side !== null,
            dimmed: lineageIndex !== null && side === null,
            lineageSide: side,
            running,
          } satisfies ProvenanceNodeData,
        };
      }),
    [positioned.nodes, lineageIndex, selectedNodeId, dragPositions, runningNodeIds],
  );

  const edges = useMemo(
    () =>
      positioned.edges.map((edge) => {
        const highlighted = lineageIndex?.edges.has(edge.id) ?? false;
        const dimmed = lineageIndex !== null && !highlighted;
        return {
          ...edge,
          animated: highlighted,
          style: {
            stroke: highlighted ? studio.blue : studio.border,
            strokeWidth: highlighted ? 2 : 1.5,
            opacity: dimmed ? 0.2 : 1,
          },
          labelStyle: {
            fill: studio.muted,
            fontSize: 10.5,
            fontFamily: 'inherit',
            opacity: dimmed ? 0.2 : 1,
          },
          labelBgStyle: { fill: studio.page, fillOpacity: dimmed ? 0.2 : 0.9 },
          labelBgPadding: [4, 2] as [number, number],
          labelBgBorderRadius: 4,
        };
      }),
    [positioned.edges, lineageIndex, studio],
  );

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void fitView({ padding: 0.18, duration: 240, maxZoom: 1 });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [positioned, fitView]);

  const handleNodeClick = useCallback<NodeMouseHandler>(
    (_event, node) => {
      const data = node.data as ProvenanceNodeData;
      // Grupo não é objeto da proveniência: clicar abre, não seleciona.
      if (isGroup(data.node)) {
        onToggleGroup(node.id);
        return;
      }
      onSelectNode(node.id === selectedNodeId ? null : node.id);
    },
    [onSelectNode, onToggleGroup, selectedNodeId],
  );

  const large = nodes.length > LARGE_GRAPH_THRESHOLD;

  return (
    <Box
      sx={{
        flexGrow: 1,
        minHeight: 0,
        position: 'relative',
        '& .react-flow__attribution': { display: 'none' },
        '& .react-flow__controls-button': {
          background: studio.surface,
          borderBottom: `1px solid ${studio.border}`,
          color: studio.ink,
          fill: 'currentColor',
        },
        '& .react-flow__controls-button:hover': { background: studio.elev },
      }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodeClick={handleNodeClick}
        onPaneClick={() => onSelectNode(null)}
        onNodesChange={handleNodesChange}
        nodesDraggable
        nodesConnectable={false}
        elementsSelectable
        proOptions={{ hideAttribution: true }}
        onlyRenderVisibleElements={large}
        minZoom={0.15}
        maxZoom={2}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={18}
          size={1}
          color={studio.grid}
        />
        <Controls showInteractive={false} position="bottom-left" />
        {showMiniMap && (
        <MiniMap
          pannable
          zoomable
          position="bottom-right"
          style={{
            width: 150,
            height: 96,
            background: studio.surface,
            border: `1px solid ${studio.border}`,
            borderRadius: 8,
          }}
          nodeStrokeWidth={6}
          maskColor={studio.page + '99'}
          nodeColor={(node) => {
            const data = node.data as ProvenanceNodeData;
            if (data.node.kind === 'TRANSFORMATION') return studio.blue;
            if (data.node.role === 'SOURCE') return studio.aqua;
            if (data.node.role === 'SINK') return studio.orange;
            return studio.muted;
          }}
        />
        )}
      </ReactFlow>
    </Box>
  );
}

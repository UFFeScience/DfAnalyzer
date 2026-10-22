import type { Edge, Node } from '@xyflow/react';
import type { GraphResponse } from '../api/catalog';
import { layoutGraph, NODE_HEIGHT, NODE_WIDTH } from './layout';

const INK = '#1b1f24';
const MUTED = '#667085';
const BORDER = '#d5dae1';
const SURFACE = '#ffffff';
const ACCENT = {
  TRANSFORMATION: '#2f6fdb',
  SOURCE: '#0e9aa7',
  SINK: '#d9822b',
  INTERMEDIATE: '#667085',
} as const;

const LABEL_MAX = 24;

export function graphToSvg(graph: Pick<GraphResponse, 'nodes' | 'edges'>, title?: string): string {
  const nodes: Node[] = graph.nodes.map((node) => ({
    id: node.id,
    position: { x: 0, y: 0 },
    data: {},
  }));
  const edges: Edge[] = graph.edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
  }));
  const placed = new Map(layoutGraph(nodes, edges).map((node) => [node.id, node.position]));

  const titleHeight = title ? 34 : 0;
  let width = 0;
  let height = 0;
  for (const position of placed.values()) {
    width = Math.max(width, position.x + NODE_WIDTH);
    height = Math.max(height, position.y + NODE_HEIGHT);
  }
  width += 24;
  height += 24 + titleHeight;

  const parts: string[] = [];
  parts.push(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" ` +
      `viewBox="0 0 ${width} ${height}" font-family="IBM Plex Sans, Segoe UI, Arial, sans-serif">`,
    '<defs><marker id="seta" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">' +
      `<path d="M0 0 10 5 0 10z" fill="${MUTED}"/></marker></defs>`,
    `<rect width="${width}" height="${height}" fill="${SURFACE}"/>`,
  );
  if (title) {
    parts.push(`<text x="24" y="24" font-size="14" font-weight="600" fill="${INK}">${escapeXml(title)}</text>`);
  }
  parts.push(`<g transform="translate(0 ${titleHeight})">`);

  for (const edge of graph.edges) {
    const from = placed.get(edge.source);
    const to = placed.get(edge.target);
    if (!from || !to) continue;
    const x1 = from.x + NODE_WIDTH;
    const y1 = from.y + NODE_HEIGHT / 2;
    const x2 = to.x;
    const y2 = to.y + NODE_HEIGHT / 2;
    const bend = Math.max(30, (x2 - x1) / 2);
    parts.push(
      `<path d="M${x1} ${y1} C${x1 + bend} ${y1} ${x2 - bend} ${y2} ${x2} ${y2}" fill="none" ` +
        `stroke="${MUTED}" stroke-width="1.2" marker-end="url(#seta)"/>`,
    );
    if (edge.label) {
      parts.push(
        `<text x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 - 5}" font-size="10" text-anchor="middle" ` +
          `fill="${MUTED}" stroke="${SURFACE}" stroke-width="3" paint-order="stroke">` +
          `${escapeXml(truncate(edge.label, LABEL_MAX))}</text>`,
      );
    }
  }

  for (const node of graph.nodes) {
    const position = placed.get(node.id);
    if (!position) continue;
    const accent =
      node.kind === 'TRANSFORMATION' ? ACCENT.TRANSFORMATION : ACCENT[node.role ?? 'INTERMEDIATE'];
    const caption =
      node.kind === 'TRANSFORMATION'
        ? 'transformação'
        : node.role === 'SOURCE'
          ? 'dataset · origem'
          : node.role === 'SINK'
            ? 'dataset · resultado'
            : 'dataset';
    parts.push(
      `<g transform="translate(${position.x} ${position.y})">`,
      `<rect width="${NODE_WIDTH}" height="${NODE_HEIGHT}" rx="8" fill="${SURFACE}" stroke="${BORDER}"/>`,
      `<rect width="4" height="${NODE_HEIGHT}" rx="2" fill="${accent}"/>`,
      `<text x="16" y="24" font-size="12.5" font-weight="600" fill="${INK}" ` +
        `font-family="IBM Plex Mono, Consolas, monospace">${escapeXml(truncate(node.tag, LABEL_MAX))}</text>`,
      `<text x="16" y="42" font-size="10.5" fill="${accent}">${caption}</text>`,
      `<title>${escapeXml(node.tag)}</title>`,
      '</g>',
    );
  }

  parts.push('</g></svg>');
  return parts.join('');
}

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

export function escapeXml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function download(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function downloadSvg(filename: string, svg: string): void {
  download(filename, new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }));
}

/** Rasteriza o SVG em escala 2x. */
export function downloadPng(filename: string, svg: string, scale = 2): Promise<void> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }));
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = image.width * scale;
      canvas.height = image.height * scale;
      const context = canvas.getContext('2d');
      URL.revokeObjectURL(url);
      if (!context) {
        reject(new Error('Este navegador não expôs um canvas 2D para gerar o PNG.'));
        return;
      }
      context.scale(scale, scale);
      context.drawImage(image, 0, 0);
      canvas.toBlob((blob) => {
        if (!blob) {
          reject(new Error('O navegador não conseguiu codificar o PNG.'));
          return;
        }
        download(filename, blob);
        resolve();
      }, 'image/png');
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('O navegador não conseguiu desenhar o SVG para gerar o PNG.'));
    };
    image.src = url;
  });
}

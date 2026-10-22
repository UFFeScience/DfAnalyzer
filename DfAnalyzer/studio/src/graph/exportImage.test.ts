import { describe, expect, it } from 'vitest';
import { graphToSvg } from './exportImage';

const graph = {
  nodes: [
    { id: 't:carregar', tag: 'carregar', kind: 'TRANSFORMATION', role: null, attributes: null },
    { id: 'd:bruto', tag: 'bruto', kind: 'DATASET', role: 'SOURCE', attributes: [] },
    { id: 'd:saida<&>"', tag: 'saida<&>"', kind: 'DATASET', role: 'SINK', attributes: [] },
  ],
  edges: [
    { id: 'e1', source: 'd:bruto', target: 't:carregar', label: 'lê' },
    { id: 'e2', source: 't:carregar', target: 'd:saida<&>"', label: 'grava' },
    { id: 'e3', source: 't:carregar', target: 'd:nao_existe', label: 'órfã' },
  ],
} as unknown as Parameters<typeof graphToSvg>[0];

describe('exportação do grafo em SVG', () => {
  const svg = graphToSvg(graph, 'meu_dataflow · versão 3');

  it('é um documento SVG bem formado (toda tag aberta é fechada, na ordem)', () => {
    // O ambiente de teste não tem DOMParser; confere o aninhamento à mão.
    const stack: string[] = [];
    for (const match of svg.matchAll(/<(\/?)([a-zA-Z]+)[^>]*?(\/?)>/g)) {
      const [, closing, name, selfClosing] = match;
      if (selfClosing) continue;
      if (closing) expect(stack.pop()).toBe(name);
      else stack.push(name);
    }
    expect(stack).toEqual([]);
    expect(svg.startsWith('<svg xmlns="http://www.w3.org/2000/svg"')).toBe(true);
  });

  it('desenha todos os nós, com o nome inteiro acessível', () => {
    const names = [...svg.matchAll(/<title>(.*?)<\/title>/g)].map((m) => m[1]);
    expect(names).toEqual(['carregar', 'bruto', 'saida&lt;&amp;&gt;&quot;']);
  });

  it('escapa o que viria do banco, em vez de quebrar o XML', () => {
    expect(svg).toContain('saida&lt;&amp;&gt;&quot;');
    expect(svg).not.toContain('saida<&>');
  });

  it('desenha só arestas cujas duas pontas existem', () => {
    expect(svg.match(/marker-end="url\(#seta\)"/g)).toHaveLength(2);
  });

  it('põe a origem à esquerda do resultado, como na tela', () => {
    const x = (tag: string) => {
      const match = new RegExp(`<g transform="translate\\(([\\d.]+) [\\d.]+\\)"><rect[^>]*/><rect[^>]*/><text[^>]*>${tag}`).exec(svg);
      return Number(match?.[1]);
    };
    expect(x('bruto')).toBeLessThan(x('carregar'));
  });

  it('leva o título quando informado', () => {
    expect(svg).toContain('meu_dataflow · versão 3');
    expect(graphToSvg(graph)).not.toContain('font-size="14"');
  });
});

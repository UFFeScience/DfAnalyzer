import { describe, expect, it } from 'vitest';
import type { GraphNode, TimelineRow } from '../api/catalog';
import { nodeSummary } from './NodeList';

const transformation = { id: 't:limpar', tag: 'limpar', kind: 'TRANSFORMATION', role: null, attributes: null } as GraphNode;
const dataset = {
  id: 'd:saida', tag: 'saida', kind: 'DATASET', role: 'SINK',
  attributes: [{ name: 'a' }, { name: 'b' }],
} as unknown as GraphNode;

describe('resumo de um nó na lista', () => {
  it('mostra tasks e duração da execução quando existem', () => {
    const row = { transformationTag: 'limpar', taskCount: 144, errorTaskCount: 0, durationSeconds: 23.1 } as TimelineRow;
    expect(nodeSummary(transformation, row)).toBe('144 tasks · 23,1 s');
  });

  it('não inventa contagem quando a transformação não tem execução', () => {
    expect(nodeSummary(transformation, undefined)).toBe('Transformação · sem execução registrada');
  });

  it('descreve o dataset pelo papel e pelo número de atributos declarados', () => {
    expect(nodeSummary(dataset, undefined)).toBe('saída · 2 atributos');
  });
});

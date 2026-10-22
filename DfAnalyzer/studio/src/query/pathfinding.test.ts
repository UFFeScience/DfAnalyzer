import { describe, expect, it } from 'vitest';
import type { GraphResponse } from '../api/catalog';
import { findDerivationPath } from './pathfinding';


/** Iprep → [preparar] → oprep → [dividir] → odiv → [balancear] → obal. */
const cadeia: GraphResponse = {
  dataflowId: 1,
  dataflowTag: 'p',
  version: 1,
  view: 'TRANSFORMATION',
  nodes: [
    { id: 't:preparar', tag: 'preparar', kind: 'TRANSFORMATION', role: null, attributes: null, tasks: null },
    { id: 't:dividir', tag: 'dividir', kind: 'TRANSFORMATION', role: null, attributes: null, tasks: null },
    { id: 't:balancear', tag: 'balancear', kind: 'TRANSFORMATION', role: null, attributes: null, tasks: null },
    { id: 'd:iprep', tag: 'iprep', kind: 'DATASET', role: 'SOURCE', attributes: [], tasks: null },
    { id: 'd:obal', tag: 'obal', kind: 'DATASET', role: 'SINK', attributes: [], tasks: null },
  ],
  edges: [
    { id: 'e1', source: 'd:iprep', target: 't:preparar', label: 'iprep' },
    { id: 'e2', source: 't:preparar', target: 't:dividir', label: 'oprep' },
    { id: 'e3', source: 't:dividir', target: 't:balancear', label: 'odiv' },
    { id: 'e4', source: 't:balancear', target: 'd:obal', label: 'obal' },
  ],
};

describe('caminho de derivação', () => {
  it('encontra o caminho entre as bordas do fluxo', () => {
    const path = findDerivationPath(cadeia, 'iprep', 'obal')!;
    expect(path.steps.map((s) => s.tag)).toEqual([
      'iprep', 'preparar', 'dividir', 'balancear', 'obal',
    ]);
    expect(path.transformations).toBe(3);
    expect(path.ambiguous).toBe(false);
  });

  it('parte de um dataset intermediário, que é aresta e não nó', () => {
    // Na visão de transformações, oprep entra pela transformação que o consome.
    const path = findDerivationPath(cadeia, 'oprep', 'obal')!;
    expect(path.steps.map((s) => s.tag)).toEqual(['dividir', 'balancear', 'obal']);
  });

  it('devolve null quando não há caminho', () => {
    expect(findDerivationPath(cadeia, 'obal', 'iprep')).toBeNull();
  });

  it('devolve null para o mesmo dataset nas duas pontas', () => {
    expect(findDerivationPath(cadeia, 'iprep', 'iprep')).toBeNull();
  });

  it('sinaliza ambiguidade quando há mais de um caminho mínimo', () => {
    const bifurcado: GraphResponse = {
      ...cadeia,
      nodes: [
        ...cadeia.nodes,
        { id: 't:alt', tag: 'alt', kind: 'TRANSFORMATION', role: null, attributes: null, tasks: null },
      ],
      edges: [
        { id: 'a', source: 'd:iprep', target: 't:preparar', label: 'iprep' },
        { id: 'b', source: 'd:iprep', target: 't:alt', label: 'iprep' },
        { id: 'c', source: 't:preparar', target: 'd:obal', label: 'obal' },
        { id: 'd', source: 't:alt', target: 'd:obal', label: 'obal' },
      ],
    };
    const path = findDerivationPath(bifurcado, 'iprep', 'obal')!;
    expect(path.ambiguous).toBe(true);
  });
});

import { describe, expect, it } from 'vitest';
import { toCsv } from './csv';

const columns = [
  { name: 'ods.texto', type: 'TEXT' },
  { name: 'ods.n', type: 'NUMERIC' },
];

describe('exportação CSV', () => {
  it('cita apenas o que precisa', () => {
    const csv = toCsv({ columns, rows: [['simples', '1'], ['com, vírgula', '2']] });
    expect(csv.split('\n')[1]).toBe('simples,1');
    expect(csv.split('\n')[2]).toBe('"com, vírgula",2');
  });

  it('dobra aspas duplas', () => {
    const csv = toCsv({ columns, rows: [['disse "oi"', '1']] });
    expect(csv.split('\n')[1]).toBe('"disse ""oi""",1');
  });

  it('cita valores com quebra de linha', () => {
    const csv = toCsv({ columns, rows: [['linha1\nlinha2', '1']] });
    expect(csv).toContain('"linha1\nlinha2"');
  });

  it('nulo vira campo vazio, não a palavra null', () => {
    // Numa coluna de texto, "null" seria indistinguível de um dado real.
    const csv = toCsv({ columns, rows: [[null, '1']] });
    expect(csv.split('\n')[1]).toBe(',1');
  });

  it('o cabeçalho traz os nomes qualificados', () => {
    expect(toCsv({ columns, rows: [] }).split('\n')[0]).toBe('ods.texto,ods.n');
  });
});

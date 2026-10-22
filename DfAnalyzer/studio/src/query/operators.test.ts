import { describe, expect, it } from 'vitest';
import { conditionToExpression, conditionsToSelections, operatorsFor, type Condition } from './operators';


function op(sql: string, type: Parameters<typeof operatorsFor>[0] = 'TEXT') {
  const found = operatorsFor(type).find((candidate) => candidate.sql === sql);
  if (!found) throw new Error(`operador ${sql} não oferecido para ${type}`);
  return found;
}

function cond(partial: Partial<Condition> & Pick<Condition, 'column' | 'operator'>): Condition {
  return { value: '', value2: '', ...partial };
}

describe('operadores por tipo', () => {
  it('não oferece comparação de magnitude para texto', () => {
    // '>' em TEXT compara lexicograficamente.
    const sqls = operatorsFor('TEXT').map((o) => o.sql);
    expect(sqls).not.toContain('>');
    expect(sqls).not.toContain('BETWEEN');
  });

  it('não oferece comparação de magnitude para caminho de arquivo', () => {
    expect(operatorsFor('FILE').map((o) => o.sql)).not.toContain('>');
  });

  it('oferece ordem para numérico, e sem aspas', () => {
    const maior = op('>', 'NUMERIC');
    expect(maior.quoted).toBe(false);
    expect(operatorsFor('NUMERIC').find((o) => o.sql === '=')!.quoted).toBe(false);
  });

  it('oferece presença para todo tipo', () => {
    for (const type of ['TEXT', 'NUMERIC', 'FILE'] as const) {
      expect(operatorsFor(type).map((o) => o.sql)).toContain('IS NULL');
    }
  });
});

describe('montagem da expressão', () => {
  it('escapa aspas simples dobrando-as', () => {
    const expression = conditionToExpression(
      cond({ column: 'ods.classe', operator: op('='), value: "O'Brien" }),
    );
    expect(expression).toBe("ods.classe = 'O''Brien'");
  });

  it('um valor que parece injeção vira literal, não código', () => {
    const expression = conditionToExpression(
      cond({ column: 'ods.classe', operator: op('='), value: "x' OR '1'='1" }),
    );
    expect(expression).toBe("ods.classe = 'x'' OR ''1''=''1'");
  });

  it('numérico não vai entre aspas', () => {
    expect(
      conditionToExpression(
        cond({ column: 'ods.n', operator: op('>', 'NUMERIC'), value: '10' }),
      ),
    ).toBe('ods.n > 10');
  });

  it('"contém" vira LIKE com curingas nas duas pontas', () => {
    expect(
      conditionToExpression(cond({ column: 'ods.t', operator: op('LIKE'), value: 'ódio' })),
    ).toBe("ods.t LIKE '%ódio%'");
  });

  it('BETWEEN precisa dos dois valores', () => {
    const between = op('BETWEEN', 'NUMERIC');
    expect(
      conditionToExpression(cond({ column: 'ods.n', operator: between, value: '1' })),
    ).toBeNull();
    expect(
      conditionToExpression(
        cond({ column: 'ods.n', operator: between, value: '1', value2: '9' }),
      ),
    ).toBe('ods.n BETWEEN 1 AND 9');
  });

  it('operador sem operando não exige valor', () => {
    expect(
      conditionToExpression(cond({ column: 'ods.t', operator: op('IS NULL') })),
    ).toBe('ods.t IS NULL');
  });

  it('condição incompleta é descartada em vez de virar 400', () => {
    const conditions = [
      cond({ column: 'ods.t', operator: op('='), value: 'ok' }),
      cond({ column: 'ods.t', operator: op('='), value: '   ' }),
      cond({ column: '', operator: op('=') , value: 'x' }),
    ];
    expect(conditionsToSelections(conditions)).toEqual(["ods.t = 'ok'"]);
  });
});

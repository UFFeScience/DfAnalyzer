import type { QueryRequest } from '../api/catalog';

export interface NotebookOptions {
  /** Endereço de onde a consulta foi exportada (`window.location.origin`). */
  baseUrl: string;
  dataflowId: number;
  dataflowTag: string;
  request: QueryRequest;
  /** SQL devolvida pela última execução na tela, se houve. */
  sql?: string;
  /** Data da exportação, já formatada. */
  exportedAt: string;
}

/** Maior página que o servidor aceita (`QueryRequest.size`). */
const PAGE_SIZE = 1000;

export function queryToNotebook(options: NotebookOptions): string {
  const { page: _page, size: _size, ...body } = options.request;
  const consulta = JSON.stringify(body, null, 2);

  const cells = [
    markdown([
      `# Consulta de proveniência — \`${options.dataflowTag}\``,
      '',
      `Exportada do DfA Studio em ${options.exportedAt}.`,
      '',
      'Este notebook **refaz a consulta** contra o DfAnalyzer, pela mesma API que a tela usa.',
      'Os dados vêm do banco no momento em que a célula roda — não são uma cópia do que',
      'estava na tela na hora da exportação.',
      '',
      'Requisitos: `requests` e `pandas`; o DfAnalyzer acessível em `DFA_URL`.',
    ]),
    code([
      'import json',
      '',
      'import pandas as pd',
      'import requests',
      '',
      '# endereço do DfAnalyzer de onde a consulta foi exportada - ajuste se mudou',
      `DFA_URL = ${pythonString(options.baseUrl)}`,
      `DATAFLOW_ID = ${options.dataflowId}`,
      '',
      '# o corpo da consulta, exatamente como a tela enviou (sem paginação)',
      `CONSULTA = json.loads(r"""${consulta}""")`,
    ]),
    code([
      `def executar(consulta, tamanho_pagina=${PAGE_SIZE}):`,
      '    """Roda a consulta página a página e devolve (DataFrame, SQL executada)."""',
      '    linhas, pagina = [], 0',
      '    while True:',
      '        corpo = dict(consulta, page=pagina, size=tamanho_pagina)',
      '        resposta = requests.post(',
      '            f"{DFA_URL}/api/v2/dataflows/{DATAFLOW_ID}/query", json=corpo, timeout=120',
      '        )',
      '        if not resposta.ok:',
      '            # o servidor explica o que recusou (coluna fora do catálogo, condição inválida)',
      '            raise RuntimeError(f"{resposta.status_code}: {resposta.text}")',
      '        dados = resposta.json()',
      '        linhas.extend(dados["rows"])',
      '        if not dados["hasMore"]:',
      '            break',
      '        pagina += 1',
      '    tabela = pd.DataFrame(linhas, columns=[c["name"] for c in dados["columns"]])',
      '    for coluna in dados["columns"]:',
      '        # as células chegam como texto; o tipo vem do catálogo do DfAnalyzer',
      '        if coluna["type"] == "NUMERIC":',
      '            tabela[coluna["name"]] = pd.to_numeric(tabela[coluna["name"]])',
      '    return tabela, dados["sql"]',
      '',
      '',
      'df, sql = executar(CONSULTA)',
      'print(sql)',
      'df',
    ]),
  ];

  if (options.sql) {
    cells.push(
      markdown([
        '## SQL executada na hora da exportação',
        '',
        'Para leitura. A célula acima imprime a SQL que o servidor gerar quando ela rodar.',
        '',
        '```sql',
        options.sql,
        '```',
      ]),
    );
  }

  return JSON.stringify(
    {
      cells,
      metadata: {
        kernelspec: { display_name: 'Python 3', language: 'python', name: 'python3' },
        language_info: { name: 'python' },
      },
      nbformat: 4,
      nbformat_minor: 5,
    },
    null,
    1,
  );
}

function lines(source: string[]): string[] {
  return source.map((line, index) => (index < source.length - 1 ? `${line}\n` : line));
}

function markdown(source: string[]) {
  return { cell_type: 'markdown', metadata: {}, source: lines(source) };
}

function code(source: string[]) {
  return { cell_type: 'code', execution_count: null, metadata: {}, outputs: [], source: lines(source) };
}

/** JSON de uma string é um literal Python válido. */
function pythonString(value: string): string {
  return JSON.stringify(value);
}

export function downloadNotebook(filename: string, content: string): void {
  const blob = new Blob([content], { type: 'application/x-ipynb+json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

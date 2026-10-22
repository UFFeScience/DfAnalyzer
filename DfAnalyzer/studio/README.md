# DfA Studio

Interface web de exploração de proveniência do DfAnalyzer: React, TypeScript e
Material UI, empacotada pelo Vite e servida pelo próprio Spring Boot, na mesma
origem da API.

## Telas

- **Dataflows** — lista com estrutura, versões, execuções, tasks, última
  execução, origem do mapeamento e status; execuções dos últimos sete dias e
  exportação do PROV-JSON de qualquer dataflow da lista.
- **Grafo** — proveniência prospectiva em duas visões (transformação como nó
  ou dataset como nó), escolha da execução mostrada, rastreio de linhagem,
  inspeção de nó com as métricas da execução, anotações, reorganização
  automática e exportação em SVG, PNG e PROV-JSON.
- **Consulta** — construtor visual sobre o catálogo, resultado paginado,
  filtro por execução e exportação em CSV e em notebook Jupyter.
- **Analytics** — versões por execuções com recorte por período, linha do
  tempo de tasks, saúde e vazão da ingestão, transações pendentes na fila,
  comparação de execuções e exportação em CSV.
- **Galeria** — perfil dos datasets e de seus atributos.

Em telas estreitas a navegação vai para uma barra inferior e o grafo pode ser
visto como lista de nós.

## Como rodar

### No container

`mvn package` compila o bundle para dentro do jar. Com o container no ar:

```
http://localhost:22000/studio
```

A interface anterior continua em `http://localhost:22000/`.

### Desenvolvimento com recarga a quente

Com o backend em `localhost:22000`:

```bash
cd DfAnalyzer/studio
npm install
npm run dev          # http://localhost:5173/studio/
```

O `vite.config.ts` encaminha `/api` e `/query_interface` para o backend. Para
outro endereço: `VITE_API_TARGET=http://localhost:22001 npm run dev`.

Outros comandos: `npm test`, `npm run build`, `npm run typecheck` e
`npm run api:types`.

## Contrato da API

`src/main/resources/static/api/openapi.yaml` é escrito à mão e é a fonte da
verdade da API v2 (`/api/v2/...`). Duas checagens o mantêm fiel ao código:

- `OpenApiContractTest` compara, no build, cada propriedade do contrato com os
  campos dos DTOs Java;
- `npm run api:types` gera `src/api/schema.d.ts`, de onde o front tira todos os
  tipos — ler um campo que o contrato não declara não compila.

Ao mudar o contrato, rode `npm run api:types` e versione o `schema.d.ts` junto.

## Estrutura

```
src/
  api/         cliente HTTP e hooks de dados
  components/  componentes básicos (cartão, chip, seletor)
  graph/       grafo (React Flow), layout, inspetor e exportação de imagem
  analytics/   linha do tempo, vazão e comparação de execuções
  query/       operadores por tipo, caminho no grafo, CSV e notebook
  layout/      moldura da aplicação, busca global e ícones
  pages/       telas
  theme/       tokens de cor e tema claro/escuro
```

O que está aberto fica na URL (`?df=2&tag=soma&sec=graph&node=...`), então um
grafo com um nó em foco pode ser compartilhado por link.

## Estados de tela

Toda tela trata os quatro estados: carregando, erro, vazio e com dados. Um
valor que o esquema não tem não é exibido — numa ferramenta de proveniência,
um número plausível e falso é pior que a ausência dele.

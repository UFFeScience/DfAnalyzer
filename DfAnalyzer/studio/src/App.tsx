import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import type { GraphView } from './api/catalog';
import { AppShell } from './layout/AppShell';
import { GlobalSearch } from './layout/GlobalSearch';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { DataflowsPage } from './pages/DataflowsPage';
import { GalleryPage } from './pages/GalleryPage';
import { GraphPage } from './pages/GraphPage';
import { QueryPage } from './pages/QueryPage';
import { ColorModeProvider } from './theme/ColorModeContext';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Proveniência só cresce; refazer a busca no foco da janela não ajuda.
      refetchOnWindowFocus: false,
      staleTime: 30_000,
      retry: 1,
    },
  },
});

type Section = 'graph' | 'query' | 'analytics' | 'gallery';

interface Selection {
  id: number;
  tag: string;
  section: Section;
  view: GraphView;
  node: string | null;
}

/** O que está aberto fica na query string (`?df=2&tag=soma`). */
function readSelection(): Selection | null {
  const params = new URLSearchParams(window.location.search);
  const id = Number(params.get('df'));
  const tag = params.get('tag');
  if (!Number.isInteger(id) || id <= 0 || !tag) {
    return null;
  }
  const view = params.get('view') === 'dataset' ? 'dataset' : 'transformation';
  const secParam = params.get('sec');
  const section: Section =
    secParam === 'query' ? 'query'
      : secParam === 'analytics' ? 'analytics'
        : secParam === 'gallery' ? 'gallery'
            : 'graph';
  return { id, tag, section, view, node: params.get('node') };
}

function writeSelection(selection: Selection | null) {
  const url = new URL(window.location.href);
  if (selection) {
    url.searchParams.delete('page');
    url.searchParams.set('df', String(selection.id));
    url.searchParams.set('tag', selection.tag);
    url.searchParams.set('sec', selection.section);
    url.searchParams.set('view', selection.view);
    if (selection.node) {
      url.searchParams.set('node', selection.node);
    } else {
      url.searchParams.delete('node');
    }
  } else {
    ['df', 'tag', 'sec', 'view', 'node'].forEach((key) => url.searchParams.delete(key));
  }
  window.history.pushState(null, '', url);
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ColorModeProvider>
        <Studio />
      </ColorModeProvider>
    </QueryClientProvider>
  );
}

function Studio() {
  const [selection, setSelection] = useState<Selection | null>(readSelection);
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    const onPopState = () => {
      setSelection(readSelection());
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setSearchOpen(true);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const select = (next: Selection | null) => {
    writeSelection(next);
    setSelection(next);
  };

  // A URL acompanha a visão e o nó em foco, para poder ser compartilhada.
  const updateSelection = (patch: Partial<Selection>) => {
    setSelection((current) => {
      if (!current) return current;
      const next = { ...current, ...patch };
      writeSelection(next);
      return next;
    });
  };

  return (
    <>
    <AppShell
      title={
        selection
          ? selection.section === 'query'
            ? 'Consulta'
            : selection.section === 'analytics'
              ? 'Analytics'
              : selection.section === 'gallery'
                ? 'Galeria'
                : 'Grafo'
          : 'Dataflows'
      }
      section={selection ? selection.section : 'dataflows'}
      hasSelection={!!selection}
      onNavigate={(next) => {
        if (next === 'dataflows') {
          select(null);
        } else if (selection) {
          updateSelection({ section: next as Section });
        }
      }}
      onOpenSearch={() => setSearchOpen(true)}
    >
      {!selection && (
        <DataflowsPage
          onOpenDataflow={(id, tag) =>
            select({ id, tag, section: 'graph', view: 'transformation', node: null })
          }
        />
      )}
      {selection?.section === 'graph' && (
        <GraphPage
          dataflowId={selection.id}
          dataflowTag={selection.tag}
          view={selection.view}
          selectedNodeId={selection.node}
          onViewChange={(view) => updateSelection({ view, node: null })}
          onSelectNode={(node) => updateSelection({ node })}
          onBack={() => select(null)}
          section={selection.section}
          onNavigateSection={(section) => updateSelection({ section })}
        />
      )}
      {selection?.section === 'query' && (
        <QueryPage
          dataflowId={selection.id}
          dataflowTag={selection.tag}
          initialDataset={selection.node?.startsWith('d:') ? selection.node.slice(2) : null}
          onBack={() => select(null)}
          section={selection.section}
          onNavigateSection={(section) => updateSelection({ section })}
        />
      )}
      {selection?.section === 'analytics' && (
        <AnalyticsPage
          dataflowId={selection.id}
          dataflowTag={selection.tag}
          onBack={() => select(null)}
          section={selection.section}
          onNavigateSection={(section) => updateSelection({ section })}
        />
      )}
      {selection?.section === 'gallery' && (
        <GalleryPage
          dataflowId={selection.id}
          dataflowTag={selection.tag}
          onBack={() => select(null)}
          section={selection.section}
          onNavigateSection={(section) => updateSelection({ section })}
        />
      )}
    </AppShell>

    {searchOpen && (
      <GlobalSearch
        onClose={() => setSearchOpen(false)}
        currentDataflow={selection ? { id: selection.id, tag: selection.tag } : null}
        onOpenDataflow={(id, tag) =>
          select({ id, tag, section: 'graph', view: 'transformation', node: null })
        }
        onSelectNode={(node) => updateSelection({ section: 'graph', node })}
      />
    )}
    </>
  );
}

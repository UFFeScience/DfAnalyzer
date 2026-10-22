/** `blue`, `aqua` e `orange` distinguem tipos de nó; as cores de estado não viram série. */
export interface StudioPalette {
  page: string;
  surface: string;
  elev: string;
  border: string;
  ink: string;
  muted: string;
  grid: string;
  blue: string;
  aqua: string;
  orange: string;
  blueDim: string;
  aquaDim: string;
  orangeDim: string;
  good: string;
  warn: string;
  serious: string;
  crit: string;
  goodDim: string;
  critDim: string;
  shadow: string;
}

const status = {
  good: '#0ca30c',
  warn: '#fab219',
  serious: '#ec835a',
  crit: '#d03b3b',
} as const;

export const lightPalette: StudioPalette = {
  ...status,
  page: '#f9f9f7',
  surface: '#fcfcfb',
  elev: '#ffffff',
  border: '#e1e0d9',
  ink: '#0b0b0b',
  muted: '#898781',
  grid: '#e1e0d9',
  blue: '#2a78d6',
  aqua: '#1baf7a',
  orange: '#eb6834',
  blueDim: 'rgba(42,120,214,.10)',
  aquaDim: 'rgba(27,175,122,.12)',
  orangeDim: 'rgba(235,104,52,.10)',
  goodDim: 'rgba(12,163,12,.10)',
  critDim: 'rgba(208,59,59,.10)',
  shadow: '0 1px 2px rgba(11,11,11,.05),0 10px 30px rgba(11,11,11,.07)',
};

export const darkPalette: StudioPalette = {
  ...status,
  page: '#0d0d0d',
  surface: '#1a1a19',
  elev: '#232320',
  border: '#2c2c2a',
  ink: '#ffffff',
  muted: '#898781',
  grid: '#2c2c2a',
  blue: '#3987e5',
  aqua: '#199e70',
  orange: '#d95926',
  blueDim: 'rgba(57,135,229,.18)',
  aquaDim: 'rgba(25,158,112,.18)',
  orangeDim: 'rgba(217,89,38,.18)',
  goodDim: 'rgba(12,163,12,.18)',
  critDim: 'rgba(208,59,59,.18)',
  shadow: '0 1px 2px rgba(0,0,0,.6),0 10px 30px rgba(0,0,0,.45)',
};

export const SANS = "'IBM Plex Sans', system-ui, -apple-system, 'Segoe UI', sans-serif";
export const MONO = "'IBM Plex Mono', ui-monospace, SFMono-Regular, Menlo, monospace";

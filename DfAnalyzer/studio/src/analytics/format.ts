/** Formato pt-BR. */
export function formatSeconds(seconds: number | null | undefined): string {
  if (seconds == null) return '—';
  if (seconds < 60) return `${seconds.toFixed(1).replace('.', ',')} s`;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds - minutes * 60);
  return `${minutes}m ${rest}s`;
}

export function formatCount(value: number | null | undefined): string {
  if (value == null) return '—';
  return value.toLocaleString('pt-BR');
}

/** `+12,3%` / `−4,0%` / `sem variação` — null quando a base é 0 ou ausente. */
export function formatPercentDelta(before: number | null, after: number | null): string | null {
  if (before == null || after == null || before === 0) return null;
  const pct = ((after - before) / before) * 100;
  if (Math.abs(pct) < 0.05) return 'sem variação';
  const sign = pct > 0 ? '+' : '−';
  return `${sign}${Math.abs(pct).toFixed(1).replace('.', ',')}%`;
}

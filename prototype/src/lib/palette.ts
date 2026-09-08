// 单色体系：所有品类共用主色，不再换色相。
// 仅用「主色实心 / 中性墨色」两档做轻微区分，保证整体统一克制。
const INK = "#1a1a1e";
const ACCENT = "#3f3ca8";
const MUTED = "#75737d";

// 核心变现品类走主色，其余走中性墨色，靠文字区分而非色相。
const STRONG = new Set(["痛点科普", "转化类"]);

export function colorOf(key: string): string {
  return STRONG.has(key) ? ACCENT : INK;
}

export function softOf(key: string): string {
  return STRONG.has(key) ? "rgba(63,60,168,0.10)" : "rgba(26,26,30,0.06)";
}

export function chipStyle(key: string) {
  return { color: colorOf(key), background: softOf(key) };
}

export { INK, ACCENT, MUTED };

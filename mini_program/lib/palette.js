const INK = "#1a1a1e";
const ACCENT = "#3f3ca8";
const MUTED = "#75737d";
const STRONG = { 痛点科普: true, 转化类: true };

function colorOf(key) {
  return STRONG[key] ? ACCENT : INK;
}

function softOf(key) {
  return STRONG[key] ? "rgba(63,60,168,0.10)" : "rgba(26,26,30,0.06)";
}

function withAlpha(hex, aa) {
  return hex + aa;
}

module.exports = { INK, ACCENT, MUTED, colorOf, softOf, withAlpha };

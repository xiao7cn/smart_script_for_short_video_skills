const { getScript } = require("../../api/script");
const { errMsg } = require("../../api/client");
const { loadOptions } = require("../../store/options");
const { defaultOptions } = require("../../data/config");
const { colorOf } = require("../../lib/palette");

function fmt(ts) {
  return ts ? ts.slice(0, 16) : "";
}

Component({
  options: { virtualHost: true },
  properties: {
    item: { type: Object, value: {} },
  },
  data: {
    detail: null,
    copied: false,
    showBreakdown: false,
    c: "#1a1a1e",
    cBg: "#1a1a1e1a",
    structure: "",
    paras: [],
    originalParas: [],
    rewriteParas: [],
  },
  lifetimes: {
    attached() {
      this.boot();
    },
  },
  observers: {
    item() {
      this.boot();
    },
  },
  methods: {
    boot() {
      const item = this.properties.item || {};
      const c = colorOf(item.scriptType || "");
      this.setData({ c, cBg: c + "1a", copied: false, showBreakdown: false, detail: null });
      loadOptions()
        .then((o) => {
          const st = (o.scriptTypes || defaultOptions.scriptTypes).find((s) => s.key === item.scriptType);
          this.setData({ structure: (st && st.formula) || "" });
        })
        .catch(() => {});
      if (!item.id) return;
      getScript(item.id)
        .then((d) => {
          const paras = (d.body || "").split("\n\n").filter(Boolean);
          const bd = d.breakdown;
          this.setData({
            detail: d,
            structure: d.structure || this.data.structure,
            paras,
            originalParas: bd && bd.original ? bd.original.split("\n") : [],
            rewriteParas: bd && bd.rewrite ? bd.rewrite.split("\n") : [],
          });
        })
        .catch((e) => {
          this.triggerEvent("toast", { message: errMsg(e) });
        });
    },
    back() {
      this.triggerEvent("close");
    },
    openBreakdown() {
      this.setData({ showBreakdown: true });
    },
    closeBreakdown() {
      this.setData({ showBreakdown: false });
    },
    copy() {
      const item = this.properties.item || {};
      const detail = this.data.detail;
      if (!detail) return;
      wx.setClipboardData({
        data: item.title + "\n\n" + detail.body,
        success: () => {
          this.setData({ copied: true });
          setTimeout(() => this.setData({ copied: false }), 1600);
        },
      });
    },
    fmtTime() {
      return fmt((this.properties.item && this.properties.item.createdAt) || "");
    },
  },
});

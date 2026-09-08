const { persona: defaultPersona } = require("../../data/config");

function clone(p) {
  return JSON.parse(JSON.stringify(p || defaultPersona));
}

Component({
  options: { virtualHost: true },
  properties: {
    persona: { type: Object, value: defaultPersona },
  },
  data: {
    editing: false,
    draft: defaultPersona,
    needDraft: "",
    banDraft: "",
  },
  lifetimes: {
    attached() {
      this.setData({ draft: clone(this.properties.persona) });
    },
  },
  observers: {
    persona(p) {
      if (!this.data.editing) this.setData({ draft: clone(p) });
    },
  },
  methods: {
    back() {
      this.triggerEvent("back");
    },
    startEdit() {
      this.setData({ editing: true, draft: clone(this.properties.persona) });
    },
    cancel() {
      this.setData({ editing: false, draft: clone(this.properties.persona) });
    },
    save() {
      this.triggerEvent("change", this.data.draft);
      this.setData({ editing: false });
    },
    reset() {
      this.triggerEvent("reset");
    },
    setField(e) {
      const k = e.currentTarget.dataset.k;
      const draft = Object.assign({}, this.data.draft);
      draft[k] = e.detail.value;
      this.setData({ draft });
    },
    setNeed(e) {
      const i = e.currentTarget.dataset.i;
      const needs = (this.data.draft.needs || []).slice();
      needs[i] = e.detail.value;
      this.setData({ "draft.needs": needs });
    },
    delNeed(e) {
      const i = e.currentTarget.dataset.i;
      const needs = (this.data.draft.needs || []).filter((_, j) => j !== i);
      this.setData({ "draft.needs": needs });
    },
    onNeedDraft(e) {
      this.setData({ needDraft: e.detail.value });
    },
    addNeed() {
      const v = (this.data.needDraft || "").trim();
      if (!v) return;
      this.setData({
        "draft.needs": (this.data.draft.needs || []).concat([v]),
        needDraft: "",
      });
    },
    delBan(e) {
      const v = e.currentTarget.dataset.v;
      this.setData({
        "draft.banned": (this.data.draft.banned || []).filter((x) => x !== v),
      });
    },
    onBanDraft(e) {
      this.setData({ banDraft: e.detail.value });
    },
    addBan() {
      const v = (this.data.banDraft || "").trim();
      if (!v || (this.data.draft.banned || []).indexOf(v) >= 0) return;
      this.setData({
        "draft.banned": (this.data.draft.banned || []).concat([v]),
        banDraft: "",
      });
    },
    pad(i) {
      return String(i + 1).padStart(2, "0");
    },
  },
});

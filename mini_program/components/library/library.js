const { defaultOptions } = require("../../data/config");
const { loadOptions } = require("../../store/options");
const { colorOf } = require("../../lib/palette");

function fmt(ts) {
  return ts ? ts.slice(0, 16) : "";
}

function decorate(items, options) {
  const list = (items || []).slice().sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
  return list.map((s) => {
    const c = colorOf(s.scriptType);
    const tc = colorOf(s.topicType);
    return Object.assign({}, s, {
      typeColor: c,
      typeBg: c + "1a",
      topicColor: tc,
      topicBg: tc + "1a",
      time: fmt(s.createdAt),
    });
  });
}

Component({
  options: { virtualHost: true },
  properties: {
    items: { type: Array, value: [] },
    total: { type: Number, value: 0 },
    initialFilter: { type: Object, value: {} },
    task: { type: Object, value: null },
  },
  data: {
    options: defaultOptions,
    typeF: "全部",
    scriptF: "全部",
    open: null,
    list: [],
    genCount: 0,
    topicTypeFilters: [],
    scriptFilters: [],
    swipeId: 0,
    swipeDx: 0,
    dragging: false,
    elapsed: 0,
    progress: null,
  },
  lifetimes: {
    attached() {
      const f = this.properties.initialFilter || {};
      this.setData({
        typeF: f.topicType || "全部",
        scriptF: f.scriptType || "全部",
      });
      loadOptions().then((o) => {
        this.setData({ options: o });
        this.refresh();
      });
      this.refresh();
    },
    detached() {
      this.stopClock();
    },
  },
  observers: {
    "items, total, task": function () {
      this.refresh();
      this.syncClock();
    },
  },
  methods: {
    refresh() {
      const opt = this.data.options;
      const items = this.properties.items || [];
      const typeF = this.data.typeF;
      const scriptF = this.data.scriptF;
      const topicTypeFilters = ["全部"].concat((opt.topicTypes || []).map((t) => t.key)).map((f) => ({
        key: f,
        active: typeF === f,
        color: colorOf(f),
      }));
      const scriptFilters = [{ key: "全部", label: "全部脚本", active: scriptF === "全部", color: "#3f3ca8" }].concat(
        (opt.scriptTypes || []).map((s) => ({
          key: s.key,
          label: s.key,
          active: scriptF === s.key,
          color: colorOf(s.key),
        })),
      );
      const task = this.properties.task;
      let progress = null;
      if (task && task.taskNo) {
        const running = (task.items || []).filter((i) => i.status === "RUNNING").length;
        const settled = (task.doneCount || 0) + (task.failCount || 0);
        progress = {
          settled,
          total: task.total,
          percent: task.total > 0 ? Math.round((settled / task.total) * 100) : 0,
          measurable: settled > 0,
          running,
          doneCount: task.doneCount || 0,
          failCount: task.failCount || 0,
        };
      }
      this.setData({
        list: decorate(items, opt),
        genCount: items.filter((s) => s.generated).length,
        topicTypeFilters,
        scriptFilters,
        progress,
      });
    },
    syncClock() {
      if (this.properties.task && this.properties.task.taskNo) {
        if (!this._clock) {
          this.setData({ elapsed: 0 });
          this._clockAt = Date.now();
          this._clock = setInterval(() => {
            this.setData({ elapsed: Math.floor((Date.now() - this._clockAt) / 1000) });
          }, 1000);
        }
      } else {
        this.stopClock();
      }
    },
    stopClock() {
      if (this._clock) {
        clearInterval(this._clock);
        this._clock = null;
      }
    },
    applyTopic(e) {
      const key = e.currentTarget.dataset.key;
      this.setData({ typeF: key, swipeId: 0, swipeDx: 0 });
      this.refresh();
      this.triggerEvent("filterchange", { topicType: key, scriptType: this.data.scriptF });
    },
    applyScript(e) {
      const key = e.currentTarget.dataset.key;
      this.setData({ scriptF: key, swipeId: 0, swipeDx: 0 });
      this.refresh();
      this.triggerEvent("filterchange", { topicType: this.data.typeF, scriptType: key });
    },
    openItem(e) {
      if (this.data.swipeDx < -20) return;
      this.setData({ open: e.currentTarget.dataset.item });
    },
    closeReader() {
      this.setData({ open: null });
    },
    onToast(e) {
      this.triggerEvent("toast", e.detail);
    },
    onDelete(e) {
      this.triggerEvent("delete", { id: e.currentTarget.dataset.id });
      this.setData({ swipeId: 0, swipeDx: 0 });
    },
    onTouchStart(e) {
      this._sx = e.touches[0].clientX;
      this._open = this.data.swipeId === e.currentTarget.dataset.id;
      this.setData({ dragging: true, swipeId: e.currentTarget.dataset.id });
    },
    onTouchMove(e) {
      const d = e.touches[0].clientX - this._sx + (this._open ? -84 : 0);
      const dx = Math.max(-84, Math.min(0, d));
      this.setData({ swipeDx: dx });
    },
    onTouchEnd() {
      const snap = this.data.swipeDx < -42;
      this.setData({ dragging: false, swipeDx: snap ? -84 : 0, swipeId: snap ? this.data.swipeId : 0 });
    },
  },
});

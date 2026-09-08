const { defaultOptions, persona: defaultPersona } = require("../../data/config");
const { loadOptions } = require("../../store/options");
const { colorOf } = require("../../lib/palette");
const { buildPrompt, gridPathOf } = require("../../lib/prompt");

const STEP_TITLE = {
  topicType: "选题类型",
  source: "选题来源",
  refs: "对标视频",
  grid: "25 宫格",
  element: "爆款元素",
  scriptType: "脚本类型",
};
const SKIP_ARR_KEYS = {
  topicType: ["topicType"],
  source: ["source"],
  grid: ["inner", "middle", "outer"],
  element: ["element"],
  scriptType: ["scriptType"],
};

function emptySel() {
  return {};
}

Component({
  options: { virtualHost: true },
  properties: {
    persona: { type: Object, value: defaultPersona },
  },
  data: {
    options: defaultOptions,
    step: 0,
    sel: emptySel(),
    showCount: false,
    askSkip: false,
    promptDraft: null,
    countN: 5,
    presets: [1, 3, 5, 10, 20],
    view: {},
  },
  lifetimes: {
    attached() {
      loadOptions().then((o) => {
        this.setData({ options: o });
        this.refresh();
      });
      this.refresh();
    },
  },
  observers: {
    persona() {
      this.refresh();
    },
  },
  methods: {
    flow() {
      const sel = this.data.sel;
      const refSources = this.data.options.refSources || [];
      const needsRefs = (sel.source || []).some((s) => refSources.indexOf(s) >= 0);
      const list = ["topicType", "source"];
      if (needsRefs) list.push("refs");
      list.push("grid", "element", "scriptType");
      return list;
    },
    has(k, v) {
      return (this.data.sel[k] || []).indexOf(v) >= 0;
    },
    hasSelection(id) {
      const sel = this.data.sel;
      switch (id) {
        case "topicType":
          return (sel.topicType || []).length > 0;
        case "source":
          return (sel.source || []).length > 0;
        case "refs":
          return !!sel.autoSearch || (sel.refs || []).filter((r) => r && r.trim()).length > 0;
        case "grid":
          return (sel.inner || []).length + (sel.middle || []).length + (sel.outer || []).length > 0;
        case "element":
          return (sel.element || []).length > 0;
        case "scriptType":
          return (sel.scriptType || []).length > 0;
        default:
          return false;
      }
    },
    refresh() {
      const flow = this.flow();
      const step = this.data.step;
      const isReview = step >= flow.length;
      const current = flow[step];
      const sel = this.data.sel;
      const opt = this.data.options;
      const persona = this.properties.persona || defaultPersona;
      const gridPath = gridPathOf(sel);
      const prompt = buildPrompt(sel, persona, opt.scriptTypes || []);
      const shownPrompt = this.data.promptDraft == null ? prompt : this.data.promptDraft;
      const bars = flow.map((id, i) => ({
        id,
        bg: i < step ? "#3f3ca8" : i === step ? "rgba(63,60,168,0.4)" : "rgba(26,26,30,0.1)",
      }));
      bars.push({ id: "review", bg: isReview ? "#3f3ca8" : "rgba(26,26,30,0.1)" });

      const mapCards = (list, keyField, colorKeyFn) =>
        (list || []).map((t) => {
          const key = typeof t === "string" ? t : t.key;
          const active = this.has(keyField, key);
          const color = colorOf(colorKeyFn ? colorKeyFn(t) : key);
          return Object.assign({}, typeof t === "string" ? { key: t } : t, {
            active,
            color,
            bg: active ? color + "12" : "",
            border: active ? color : "",
            tagBg: color + "1a",
          });
        });

      this.setData({
        view: {
          flow,
          TOTAL: flow.length,
          isReview,
          current: current || "",
          stepLabel: isReview
            ? "收尾 · 确认生成"
            : "第 " + (step + 1) + " / " + flow.length + " 步 · " + (STEP_TITLE[current] || ""),
          kicker: "STEP " + String((isReview ? flow.length : step) + 1).padStart(2, "0") + " · " +
            (isReview ? "确认" : current === "topicType" ? "战略意图" : current === "source" ? "灵感来源" : current === "refs" ? "对标视频" : current === "grid" ? "玩转宫格" : current === "element" ? "加钩子" : "成稿骨架"),
          title: isReview
            ? "给这批文案定个调"
            : current === "topicType"
              ? "这条内容，你想干嘛？"
              : current === "source"
                ? "真实需求从哪儿挖？"
                : current === "refs"
                  ? "添加对标视频链接"
                  : current === "grid"
                    ? "内圈 × 中圈 × 外圈"
                    : current === "element"
                      ? "套一个爆款元素"
                      : "用哪套脚本结构？",
          sub: isReview
            ? "补一句选题方向（可选），或直接生成 —— 空着的参数会自动补齐。"
            : current === "topicType"
              ? "可多选、可不选、也可跳过 —— 多选时按批次轮流出稿。"
              : current === "source"
                ? "可多选。选到「对标 / 评论」类来源，下一步会进入对标视频添加页。"
                : current === "refs"
                  ? "贴上想拆解的爆款视频 / 帖子链接，可加多条；也可让系统自动搜。"
                  : current === "grid"
                    ? "三层都可多选、可不选，随手配对就能引出话题。"
                    : current === "element"
                      ? "可多选，给平平的话题加一层抓人的味道，也可留空。"
                      : "可多选，批次内轮流套用；不选则按 4:1:3:2 的黄金配比随机分配。",
          bars,
          topicTypes: mapCards(opt.topicTypes, "topicType"),
          topicSources: mapCards(opt.topicSources, "source", () => "破圈类"),
          gridInner: mapCards(opt.gridInner, "inner"),
          gridMiddle: mapCards(opt.gridMiddle, "middle"),
          gridOuter: mapCards(opt.gridOuter, "outer"),
          viralElements: (opt.viralElements || []).map((e) => ({
            key: e.key,
            hint: e.hint,
            active: this.has("element", e.key),
          })),
          scriptTypes: (opt.scriptTypes || []).map((s) => {
            const active = this.has("scriptType", s.key);
            const color = colorOf(s.key);
            return Object.assign({}, s, {
              active,
              color,
              bg: active ? color + "12" : "",
              border: active ? color : "",
              tag: "权重 " + s.ratio + "｜" + s.goal,
              tagBg: color + "1a",
            });
          }),
          gridPath,
          refs: sel.refs && sel.refs.length ? sel.refs : [""],
          refCount: (sel.refs || []).filter((r) => r && r.trim()).length,
          autoSearch: !!sel.autoSearch,
          topicDraft: sel.topicDraft || "",
          topicPh: gridPath
            ? "例：" + ((sel.middle && sel.middle[0]) || "AI就业") + "这块，" + ((sel.outer && sel.outer[0]) || "普通人") + "最容易踩的坑是什么"
            : "一句话说清这批内容大致要聊什么（留空则由参数自动拟定）",
          shownPrompt,
          promptEdited: this.data.promptDraft != null,
          deaiPrompt: opt.deaiPrompt || "",
        },
      });
    },
    toggle(e) {
      const { k, v } = e.currentTarget.dataset;
      const sel = Object.assign({}, this.data.sel);
      const cur = (sel[k] || []).slice();
      const i = cur.indexOf(v);
      if (i >= 0) cur.splice(i, 1);
      else cur.push(v);
      sel[k] = cur;
      this.setData({ sel });
      this.refresh();
    },
    next() {
      const flow = this.flow();
      this.setData({ step: Math.min(this.data.step + 1, flow.length) });
      this.refresh();
    },
    back() {
      this.setData({ step: Math.max(this.data.step - 1, 0) });
      this.refresh();
    },
    skip() {
      const flow = this.flow();
      const current = flow[this.data.step];
      if (current) {
        const sel = Object.assign({}, this.data.sel);
        (SKIP_ARR_KEYS[current] || []).forEach((k) => {
          delete sel[k];
        });
        if (current === "refs") {
          delete sel.refs;
          delete sel.autoSearch;
        }
        this.setData({ sel });
      }
      this.next();
    },
    refine() {
      const flow = this.flow();
      const current = flow[this.data.step];
      if (current && !this.hasSelection(current)) {
        this.setData({ askSkip: true });
        return;
      }
      this.next();
    },
    closeAsk() {
      this.setData({ askSkip: false });
    },
    confirmSkip() {
      this.setData({ askSkip: false });
      this.skip();
    },
    toggleAuto() {
      const sel = Object.assign({}, this.data.sel, { autoSearch: !this.data.sel.autoSearch });
      this.setData({ sel });
      this.refresh();
    },
    clearGrid() {
      const sel = Object.assign({}, this.data.sel, { inner: [], middle: [], outer: [] });
      this.setData({ sel });
      this.refresh();
    },
    onRefInput(e) {
      const i = e.currentTarget.dataset.i;
      const rows = (this.data.view.refs || [""]).slice();
      rows[i] = e.detail.value;
      const sel = Object.assign({}, this.data.sel, { refs: rows });
      this.setData({ sel });
      this.refresh();
    },
    removeRef(e) {
      const i = e.currentTarget.dataset.i;
      const rows = (this.data.view.refs || [""]).filter((_, j) => j !== i);
      const sel = Object.assign({}, this.data.sel, { refs: rows });
      this.setData({ sel });
      this.refresh();
    },
    addRef() {
      const rows = (this.data.view.refs || [""]).concat([""]);
      const sel = Object.assign({}, this.data.sel, { refs: rows });
      this.setData({ sel });
      this.refresh();
    },
    onTopicDraft(e) {
      const sel = Object.assign({}, this.data.sel, { topicDraft: e.detail.value });
      this.setData({ sel });
      this.refresh();
    },
    onPrompt(e) {
      this.setData({ promptDraft: e.detail.value });
      this.refresh();
    },
    resetPrompt() {
      this.setData({ promptDraft: null });
      this.refresh();
    },
    copyPrompt() {
      const text = this.data.view.shownPrompt + "\n\n---\n去 AI 味二次改写：\n" + this.data.view.deaiPrompt;
      wx.setClipboardData({ data: text });
    },
    openCount() {
      this.setData({ showCount: true });
    },
    closeCount() {
      this.setData({ showCount: false });
    },
    setPreset(e) {
      this.setData({ countN: e.currentTarget.dataset.n });
    },
    decCount() {
      this.setData({ countN: Math.max(1, this.data.countN - 1) });
    },
    incCount() {
      this.setData({ countN: Math.min(20, this.data.countN + 1) });
    },
    confirmCount() {
      const n = Math.max(1, Math.min(20, this.data.countN));
      this.setData({ showCount: false });
      this.triggerEvent("generate", {
        sel: this.data.sel,
        count: n,
        promptOverride: this.data.promptDraft == null ? undefined : this.data.promptDraft,
      });
    },
    restart() {
      this.setData({ step: 0 });
      this.refresh();
    },
    noop() {},
  },
});

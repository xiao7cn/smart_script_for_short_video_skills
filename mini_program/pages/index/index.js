const { session } = require("../../store/session");
const { persona: defaultPersona } = require("../../data/config");
const { logout: apiLogout, me } = require("../../api/auth");
const { errCode, errMsg } = require("../../api/client");
const { recharge: apiRecharge } = require("../../api/credit");
const { getPersona, resetPersona, savePersona } = require("../../api/persona");
const { deleteScript, generate, listScripts } = require("../../api/script");
const { getTask } = require("../../api/task");

const SETTLED = { SUCCESS: 1, PARTIAL: 1, FAILED: 1, CANCELLED: 1 };
const POLL_MS = 2000;
const POLL_TIMEOUT = 10 * 60 * 1000;

Page({
  data: {
    statusBarHeight: 20,
    tab: "builder",
    libFilter: {},
    items: [],
    total: 0,
    persona: defaultPersona,
    user: null,
    credits: 0,
    stats: { total: 0, generated: 0 },
    task: null,
    toast: "",
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: (app.globalData && app.globalData.statusBarHeight) || 20,
      user: session.user(),
    });
    this._off = session.subscribe((s) => {
      if (s) return;
      this.stopPoll();
      this.setData({
        user: null,
        items: [],
        total: 0,
        credits: 0,
        stats: { total: 0, generated: 0 },
        persona: defaultPersona,
        task: null,
        tab: "builder",
      });
    });
    if (session.token()) this.bootstrap();
  },

  onUnload() {
    this.stopPoll();
    if (this._off) this._off();
    this.clearToastTimer();
  },

  onShow() {
    this._hidden = false;
    if (this._taskNo) this.tickTask();
  },

  onHide() {
    this._hidden = true;
  },

  flash(msg) {
    this.setData({ toast: msg });
    this.clearToastTimer();
    this._toastTimer = setTimeout(() => this.setData({ toast: "" }), 2000);
  },

  clearToastTimer() {
    if (this._toastTimer) {
      clearTimeout(this._toastTimer);
      this._toastTimer = null;
    }
  },

  async loadScripts(f, silent) {
    const filter = f || this._filter || {};
    this._filter = filter;
    try {
      const page = await listScripts({
        topicType: filter.topicType === "全部" ? undefined : filter.topicType,
        scriptType: filter.scriptType === "全部" ? undefined : filter.scriptType,
      });
      this.setData({ items: page.records || [], total: page.total || 0 });
    } catch (e) {
      if (!silent) this.flash(errMsg(e));
    }
  },

  async syncAccount() {
    const info = await me();
    session.setUser(info.user);
    this.setData({ user: info.user, credits: info.credits, stats: info.stats });
  },

  async bootstrap() {
    try {
      await this.syncAccount();
    } catch (e) {
      if (errCode(e) !== 401) this.flash(errMsg(e));
      session.clear();
      return;
    }
    getPersona()
      .then((p) => this.setData({ persona: p }))
      .catch(() => {});
    this.loadScripts({});
  },

  onAuth(e) {
    const r = e.detail;
    session.set({ token: r.token, user: r.user });
    this.setData({ user: r.user, credits: r.credits, tab: "builder" });
    this.bootstrap();
  },

  onLogout() {
    apiLogout().catch(() => {});
    session.clear();
  },

  setTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
  },

  async onGenerate(e) {
    const { sel, count, promptOverride } = e.detail;
    try {
      const res = await generate(sel, count, promptOverride);
      this.setData({
        credits: res.creditsBalance,
        libFilter: {},
        tab: "library",
      });
      this.startPoll(res.taskNo);
      this.loadScripts({}, true);
      this.flash(
        res.message ||
          (res.accepted < count
            ? "额度仅够 " + res.accepted + " 条，已开始生成"
            : "已提交 " + res.accepted + " 条，正在生成"),
      );
    } catch (err) {
      if (errCode(err) === 3001) {
        this.flash("额度不足，请先充值");
        this.setData({ tab: "profile" });
        return;
      }
      this.flash(errMsg(err));
    }
  },

  startPoll(taskNo) {
    this.stopPoll();
    this._taskNo = taskNo;
    this._settledCount = -1;
    this._pollStarted = Date.now();
    this.tickTask();
    this._pollTimer = setInterval(() => this.tickTask(), POLL_MS);
  },

  stopPoll() {
    this._taskNo = null;
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },

  async tickTask() {
    if (!this._taskNo || this._hidden) return;
    if (Date.now() - this._pollStarted > POLL_TIMEOUT) {
      this.stopPoll();
      this.setData({ task: null });
      this.flash("仍在生成，稍后刷新查看");
      return;
    }
    try {
      const t = await getTask(this._taskNo);
      this.setData({ task: t });
      const done = (t.doneCount || 0) + (t.failCount || 0);
      if (done !== this._settledCount) {
        this._settledCount = done;
        this.loadScripts(this._filter, true);
      }
      if (SETTLED[t.status]) {
        this.stopPoll();
        this.setData({ task: null });
        this.loadScripts(this._filter, true);
        this.syncAccount().catch(() => {});
      }
    } catch (e) {
      /* 单次失败不打断 */
    }
  },

  onFilter(e) {
    const f = e.detail || {};
    this.setData({ libFilter: f });
    this.loadScripts(f);
  },

  async onDelete(e) {
    const id = e.detail && e.detail.id;
    if (!id) return;
    try {
      await deleteScript(id);
      const items = (this.data.items || []).filter((s) => s.id !== id);
      this.setData({ items, total: Math.max(0, this.data.total - 1) });
      this.syncAccount().catch(() => {});
    } catch (err) {
      this.flash(errMsg(err));
    }
  },

  onToast(e) {
    const msg = (e.detail && (e.detail.message || e.detail)) || "";
    if (msg) this.flash(typeof msg === "string" ? msg : "");
  },

  async onRecharge(e) {
    const pack = e.detail;
    try {
      const r = await apiRecharge(pack.packId);
      this.setData({ credits: r.balance });
      this.flash("充值成功，到账 " + pack.base + " 条" + (pack.bonus ? " + 赠 " + pack.bonus + " 条" : ""));
    } catch (err) {
      this.flash(errMsg(err));
    }
  },

  async onPersonaChange(e) {
    const p = e.detail;
    this.setData({ persona: p });
    try {
      const saved = await savePersona(p);
      this.setData({ persona: saved });
    } catch (err) {
      this.flash(errMsg(err));
    }
  },

  async onPersonaReset() {
    try {
      const p = await resetPersona();
      this.setData({ persona: p });
      this.flash("已恢复默认人设");
    } catch (err) {
      this.flash(errMsg(err));
    }
  },
});

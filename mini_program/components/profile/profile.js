const { getPacks } = require("../../api/credit");

const DEFAULT_PACKS = [
  { packId: "p19", priceFen: 1900, base: 30, bonus: 0 },
  { packId: "p59", priceFen: 5900, base: 100, bonus: 15 },
  { packId: "p149", priceFen: 14900, base: 300, bonus: 60 },
  { packId: "p399", priceFen: 39900, base: 1000, bonus: 300 },
];

Component({
  options: { virtualHost: true },
  properties: {
    user: { type: Object, value: {} },
    stats: { type: Object, value: { total: 0, generated: 0 } },
    credits: { type: Number, value: 0 },
    persona: { type: Object, value: {} },
  },
  data: {
    showRecharge: false,
    showPersona: false,
    packs: DEFAULT_PACKS,
    packSel: 1,
    initial: "U",
    isWechat: false,
  },
  lifetimes: {
    attached() {
      this.syncUser();
      this.setData({ packs: this.decorate(DEFAULT_PACKS) });
      getPacks()
        .then((list) => {
          if (list && list.length) this.setData({ packs: this.decorate(list) });
        })
        .catch(() => {});
    },
  },
  observers: {
    user() {
      this.syncUser();
    },
  },
  methods: {
    syncUser() {
      const user = this.properties.user || {};
      const name = user.nickname || user.account || "U";
      this.setData({
        initial: String(name).slice(0, 1).toUpperCase(),
        isWechat: user.via === "wechat",
      });
    },
    openRecharge() {
      this.setData({ showRecharge: true });
    },
    closeRecharge() {
      this.setData({ showRecharge: false });
    },
    selPack(e) {
      this.setData({ packSel: e.currentTarget.dataset.i });
    },
    confirmRecharge() {
      const packs = this.data.packs;
      const pack = packs[Math.min(this.data.packSel, packs.length - 1)];
      this.setData({ showRecharge: false });
      this.triggerEvent("recharge", pack);
    },
    openPersona() {
      this.setData({ showPersona: true });
    },
    closePersona() {
      this.setData({ showPersona: false });
    },
    onPersonaChange(e) {
      this.triggerEvent("personachange", e.detail);
    },
    onPersonaReset() {
      this.triggerEvent("personareset");
    },
    logout() {
      this.triggerEvent("logout");
    },
    decorate(list) {
      return (list || []).map((p) =>
        Object.assign({}, p, {
          yuan: p.priceFen / 100,
          total: p.base + p.bonus,
        }),
      );
    },
    noop() {},
  },
});

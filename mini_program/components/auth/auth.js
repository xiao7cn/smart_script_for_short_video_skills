const { sendSmsCode, smsLogin, wechatLogin } = require("../../api/auth");
const { errMsg } = require("../../api/client");

Component({
  options: { virtualHost: true },
  data: {
    method: "wechat",
    phone: "",
    code: "",
    devCode: "",
    codeSent: false,
    left: 0,
    err: "",
    busy: false,
  },
  lifetimes: {
    detached() {
      this.clearTimer();
    },
  },
  methods: {
    setWechat() {
      this.setData({ method: "wechat", err: "" });
    },
    setPhone() {
      this.setData({ method: "phone", err: "" });
    },
    onPhone(e) {
      this.setData({ phone: String(e.detail.value || "").replace(/\D/g, "").slice(0, 11) });
    },
    onCode(e) {
      this.setData({ code: String(e.detail.value || "").replace(/\D/g, "").slice(0, 6) });
    },
    phoneOk() {
      return /^1\d{10}$/.test(this.data.phone);
    },
    clearTimer() {
      if (this._timer) {
        clearInterval(this._timer);
        this._timer = null;
      }
    },
    async sendCode() {
      this.setData({ err: "" });
      if (!this.phoneOk()) {
        this.setData({ err: "请输入正确的 11 位手机号" });
        return;
      }
      if (this.data.busy || this.data.left > 0) return;
      this.setData({ busy: true });
      try {
        const res = await sendSmsCode(this.data.phone);
        this.setData({
          codeSent: true,
          devCode: res.devCode || "",
          left: res.cooldown || 60,
        });
        this.clearTimer();
        this._timer = setInterval(() => {
          const next = this.data.left - 1;
          this.setData({ left: next });
          if (next <= 0) this.clearTimer();
        }, 1000);
      } catch (e) {
        this.setData({ err: errMsg(e) });
      } finally {
        this.setData({ busy: false });
      }
    },
    async loginPhone() {
      this.setData({ err: "" });
      if (!this.phoneOk()) {
        this.setData({ err: "请输入正确的 11 位手机号" });
        return;
      }
      if (!this.data.codeSent) {
        this.setData({ err: "请先获取验证码" });
        return;
      }
      if (this.data.busy) return;
      this.setData({ busy: true });
      try {
        const r = await smsLogin(this.data.phone, this.data.code.trim());
        this.triggerEvent("auth", r);
      } catch (e) {
        this.setData({ err: errMsg(e) });
      } finally {
        this.setData({ busy: false });
      }
    },
    async loginWechat() {
      this.setData({ err: "" });
      if (this.data.busy) return;
      this.setData({ busy: true });
      try {
        // 测试号 / touristappid 没有 code2session，后端演示实现认 wx_demo_ 前缀。
        // 正式 appId 才走 wx.login 的真实 code。
        let code = "";
        const account = wx.getAccountInfoSync && wx.getAccountInfoSync();
        const appId = account && account.miniProgram && account.miniProgram.appId;
        const official = appId && appId !== "touristappid" && !/^wx000000/.test(appId);
        if (official) {
          try {
            const login = await wx.login();
            code = (login && login.code) || "";
          } catch (e) {
            code = "";
          }
        }
        if (!code) {
          code = "wx_demo_" + Math.random().toString(36).slice(2, 10);
        }
        const r = await wechatLogin(code);
        this.triggerEvent("auth", r);
      } catch (e) {
        this.setData({ err: errMsg(e) });
      } finally {
        this.setData({ busy: false });
      }
    },
  },
});

const { api } = require("./client");

const sendSmsCode = (phone) => api.post("/auth/sms/code", { phone });
const smsLogin = (phone, code) => api.post("/auth/sms/login", { phone, code });
const wechatLogin = (code, phoneCode) => api.post("/auth/wechat/login", { code, phoneCode });
const me = () => api.get("/auth/me");
const logout = () => api.post("/auth/logout");

module.exports = { sendSmsCode, smsLogin, wechatLogin, me, logout };

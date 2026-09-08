import { api } from "./client";
import type { LoginResult, MeResult, SmsCodeResult } from "./types";

export const sendSmsCode = (phone: string) =>
  api.post<SmsCodeResult>("/auth/sms/code", { phone });

export const smsLogin = (phone: string, code: string) =>
  api.post<LoginResult>("/auth/sms/login", { phone, code });

export const wechatLogin = (code: string, phoneCode?: string) =>
  api.post<LoginResult>("/auth/wechat/login", { code, phoneCode });

export const me = () => api.get<MeResult>("/auth/me");

export const logout = () => api.post<null>("/auth/logout");

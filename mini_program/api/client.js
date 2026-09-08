const { API_BASE } = require("../config");
const { session } = require("../store/session");

function ApiError(code, message, requestId) {
  const e = new Error(message);
  e.name = "ApiError";
  e.code = code;
  e.requestId = requestId;
  return e;
}

function parsePayload(raw) {
  if (raw == null || raw === "") return null;
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }
  return raw;
}

function request(method, path, body) {
  return new Promise((resolve, reject) => {
    const header = {};
    const token = session.token();
    if (token) header.Authorization = "Bearer " + token;

    const hasBody = method !== "GET" && method !== "DELETE";
    // 必须小写 content-type + 自己 JSON.stringify：
    // 微信开发者工具里若不这么做，POST 常被编成 form，Spring @RequestBody 读失败。
    if (hasBody) header["content-type"] = "application/json";

    wx.request({
      url: API_BASE + path,
      method,
      data: hasBody ? JSON.stringify(body === undefined ? {} : body) : undefined,
      header,
      dataType: "json",
      timeout: 60000,
      success(res) {
        const payload = parsePayload(res.data);
        if (res.statusCode === 401 || (payload && payload.code === 401)) {
          session.clear();
          reject(ApiError(401, (payload && payload.message) || "登录已过期，请重新登录"));
          return;
        }
        if (!payload || typeof payload.code !== "number") {
          const hint =
            res.statusCode === 200
              ? "服务返回了无法解析的内容，请确认 API_BASE 指向 Java 后端"
              : "服务异常（HTTP " + res.statusCode + "）";
          reject(ApiError(res.statusCode || -1, hint));
          return;
        }
        if (payload.code !== 0) {
          reject(ApiError(payload.code, payload.message || "请求失败", payload.requestId));
          return;
        }
        resolve(payload.data);
      },
      fail(err) {
        const raw = (err && err.errMsg) || "";
        let msg = "网络连接失败，请稍后重试";
        if (/url not in domain list|不在|合法域名/i.test(raw)) {
          msg =
            "域名未放行：请在小程序后台把 https://xiao7ai.com 加进 request 合法域名，" +
            "开发者工具里可临时勾选「不校验合法域名」";
        } else if (/127\.0\.0\.1|localhost/.test(API_BASE) && /fail|timeout/i.test(raw)) {
          msg = "连不上后端。模拟器可用 127.0.0.1；真机请把 config.js 改成电脑局域网 IP";
        } else if (raw) {
          msg = "网络连接失败：" + raw.replace(/^request:fail\s*/i, "");
        }
        reject(ApiError(-1, msg));
      },
    });
  });
}

function qs(params) {
  const parts = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== "")
    .map((k) => encodeURIComponent(k) + "=" + encodeURIComponent(String(params[k])));
  return parts.length ? "?" + parts.join("&") : "";
}

const api = {
  get: (path) => request("GET", path),
  post: (path, body) => request("POST", path, body === undefined ? {} : body),
  put: (path, body) => request("PUT", path, body === undefined ? {} : body),
  del: (path) => request("DELETE", path),
};

function errCode(e) {
  return e && typeof e.code === "number" ? e.code : -1;
}

function errMsg(e) {
  return e && e.message ? e.message : "网络异常，请稍后重试";
}

module.exports = { api, qs, errCode, errMsg, ApiError };

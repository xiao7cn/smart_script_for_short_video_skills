// 后端接口前缀。
//
// 生产走 nginx 反代：https://xiao7ai.com/shanchuang/api → 服务器 127.0.0.1:8098/api
// 服务器部署细节见 deploy/README.md。
//
// 真机预览与正式版要求 https，且域名必须在小程序后台
//「开发管理 → 开发设置 → 服务器域名 → request 合法域名」里加上 https://xiao7ai.com，
// 否则请求会被拦成「url not in domain list」。开发者工具可临时勾选「不校验合法域名」绕过。
//
// 本地联调改回这一行（同时需要关闭合法域名校验）：
// const API_BASE = "http://127.0.0.1:8080/api";
const API_BASE = "https://xiao7ai.com/shanchuang/api";

module.exports = { API_BASE };

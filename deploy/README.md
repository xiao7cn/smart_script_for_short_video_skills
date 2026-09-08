# 部署说明

生产地址：`https://xiao7ai.com/shanchuang/api`

日常更新用 `./deploy/deploy.sh`（构建 → 上传 → 重启 → 验证）。本文件记录首次搭建做了什么，以及几个不写下来就会重踩的坑。

---

## 这台机器不是我们独占的

`47.97.91.76` 上还跑着 speedlove、finance_news、ai_quant、cex、findjob 等业务，nginx 和 MySQL 都是共用的。所以：

- **不动全局**。Node 运行时装在 `/opt/shanchuang/node`，没升级 `/usr/bin/node`；数据库用独立账号 `shanchuang`，没用 root。
- **nginx 改的是共享配置** `/etc/nginx/conf.d/www.xiao7ai.com.conf`，改之前按同机惯例备份成 `.bak.shanchuang.<时间戳>`。
- **资源紧张**：内存 7.3G 已用 5G，磁盘 20G 已用 83%。Java 堆压到 512m，Node 压到 384m，别再往上加。

## 部署结构

| 组件 | 位置 | 端口 | systemd |
| --- | --- | --- | --- |
| Java 后端 | `/opt/shanchuang/backend/app.jar` | 8098 | `shanchuang-backend` |
| agent sidecar | `/opt/shanchuang/agent/dist/server.js` | 8790（仅回环） | `shanchuang-agent` |
| Node 运行时 | `/opt/shanchuang/node/bin/node` | — | — |
| skills 脚本 | `/opt/shanchuang/skills` | — | — |
| 上传目录 | `/opt/shanchuang/data/uploads` | — | — |
| 日志 | `/opt/shanchuang/logs/{backend,agent}.log` | — | — |

配置文件：后端 `/opt/shanchuang/backend/env.sh`，agent `/opt/shanchuang/agent/.env`，都是 600，含密钥，不入库。

nginx 只加了一个 location，把 `/shanchuang/api/` 转到 `127.0.0.1:8098/api/`。

---

## 三个坑

### 1. Node 必须 ≥ 22.19.0，系统自带的 22.16.0 不行

`pi-ai` 和 `pi-agent-core` 的 `engines` 要求 `>=22.19.0`。用系统的 22.16.0 跑，**服务能正常启动、健康检查也通过**，但一发真实请求就报：

```
PROVIDER_ERROR Stream ended without finish_reason
```

排查时容易怀疑到网络或密钥上去——实际上在同一台机器上 `curl` 打 Kimi 的流式接口完全正常，`finish_reason` 和 `[DONE]` 都在。是 Node 那几个小版本之间的 fetch/stream 行为差异。

`npm ci` 当时打印过 `EBADENGINE` 警告，但那只是 warning，装是装上了。

所以装了独立运行时 `/opt/shanchuang/node`，systemd 的 `ExecStart` 指到它。升级 Node 时注意仍要 ≥ 依赖要求的版本。

### 2. agent 绝不能监听 0.0.0.0

`/v1/harness/run` 没有任何鉴权，谁都能拿它去烧模型额度。这台机器公网可达，所以 systemd 里写死了 `AGENT_HOST=127.0.0.1`。

后端的 8098 监听的是所有网卡（Spring Boot 默认），目前靠阿里云安全组只放行 80/443 挡在外面——同机 findjob 的 8099 也是这个状态。**不要在安全组里开放 8098**，否则等于绕开 nginx 直接暴露后端。

### 3. `SMS_ECHO_CODE=true` 是临时状态

目前没接真实短信通道，验证码通过接口回显（`data.devCode`），否则谁都登录不了。**这等于任何人都能登录任意手机号**。接入短信服务商后必须把 `env.sh` 里这行改成 `false` 并重启后端。

---

## 首次搭建做过的事

按顺序，重装机器时照做：

```bash
# 1. 建库与独立数据库用户（密码随机生成，直接写进 env.sh，不经过人手）
mysql -uroot -p <<'SQL'
CREATE DATABASE shanchuang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shanchuang'@'localhost' IDENTIFIED BY '<随机密码>';
GRANT ALL PRIVILEGES ON shanchuang.* TO 'shanchuang'@'localhost';
SQL

# 2. 导入表结构与种子数据
mysql -ushanchuang -p shanchuang < deploy/sql/01-schema.sql
mysql -ushanchuang -p shanchuang < deploy/sql/02-data.sql

# 3. 装独立 Node（版本要满足 pi-ai 的 engines 要求）
VER=$(curl -s https://nodejs.org/dist/index.json | python3 -c "import json,sys;print(next(r['version'] for r in json.load(sys.stdin) if r['version'].startswith('v22.')))")
curl -fsSL "https://nodejs.org/dist/$VER/node-$VER-linux-x64.tar.xz" -o /tmp/n.tar.xz
mkdir -p /opt/shanchuang/node && tar xJf /tmp/n.tar.xz -C /opt/shanchuang/node --strip-components=1

# 4. 写 /opt/shanchuang/backend/env.sh 与 /opt/shanchuang/agent/.env（chmod 600）
# 5. 写两个 systemd unit 并 enable
# 6. nginx 加 location（先备份再改，改完 nginx -t 再 reload）
```

后端需要的环境变量：`DB_URL` `DB_USER` `DB_PASSWORD` `JWT_SECRET` `SMS_ECHO_CODE` `STORAGE_DIR` `HARNESS_PROVIDER` `HARNESS_ENDPOINT` `SERVER_PORT`。

agent 需要：`DEEPSEEK_API_KEY` `MOONSHOT_API_KEY` `MOONSHOTAI_CN_API_KEY`（与前者同值，见下）`SKILLS_DIR` `AGENT_PORT`。

`MOONSHOTAI_CN_API_KEY` 是别名：agent 的健康检查按 `<PROVIDER>_API_KEY` 规则猜变量名，`moonshotai-cn` 猜出来是 `MOONSHOTAI_CN_API_KEY`，跟实际用的 `MOONSHOT_API_KEY` 对不上，不补这个别名，启动日志会少列一个 provider，看着像没配好。真实调用读的是数据库 `sv_ai_model_config.api_key_env`，不受影响。

---

## 排查

```bash
ssh -i deploy/xiao7ai.com_ECS.pem root@47.97.91.76

systemctl status shanchuang-backend shanchuang-agent
tail -f /opt/shanchuang/logs/backend.log
tail -f /opt/shanchuang/logs/agent.log

# harness 是否活着
curl -s http://127.0.0.1:8790/v1/harness/health | head -c 200

# 绕过 nginx 直连后端，用来区分是应用问题还是反代问题
curl -s http://127.0.0.1:8098/api/options | head -c 200
```

模型配置在数据库里改，改完 60 秒内生效，不用重启也不用发版：

```sql
SELECT scene, provider, model_id, temperature FROM sv_ai_model_config;
```

改 Kimi 那两行的 temperature 前先读 `deploy/sql/02-data.sql` 里的注释，它只接受 0.6，填别的会静默返回空文案。

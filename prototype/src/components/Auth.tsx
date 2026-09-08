import { useEffect, useRef, useState } from "react";
import Logo from "./Logo";

export type User = { name: string; account: string; via: "wechat" | "phone" };

type Stored = Record<string, { name: string }>;

const LS_USERS = "kbwf.users.v1";

function loadUsers(): Stored {
  try {
    return JSON.parse(localStorage.getItem(LS_USERS) || "{}") as Stored;
  } catch {
    return {};
  }
}
function saveUsers(u: Stored) {
  try {
    localStorage.setItem(LS_USERS, JSON.stringify(u));
  } catch {
    /* ignore */
  }
}

const maskPhone = (p: string) => (p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : p);

export default function Auth({ onAuth }: { onAuth: (u: User) => void }) {
  const [method, setMethod] = useState<"wechat" | "phone">("wechat");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [sentCode, setSentCode] = useState(""); // 演示用：本机生成的验证码
  const [left, setLeft] = useState(0); // 重发倒计时
  const [err, setErr] = useState("");
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) clearInterval(timer.current);
    },
    [],
  );

  const phoneOk = /^1\d{10}$/.test(phone);

  const sendCode = () => {
    setErr("");
    if (!phoneOk) {
      setErr("请输入正确的 11 位手机号");
      return;
    }
    const c = String(Math.floor(100000 + Math.random() * 900000));
    setSentCode(c);
    setLeft(60);
    timer.current && clearInterval(timer.current);
    timer.current = setInterval(() => {
      setLeft((v) => {
        if (v <= 1 && timer.current) clearInterval(timer.current);
        return v - 1;
      });
    }, 1000);
  };

  // 手机号登录：首登即注册，无需密码
  const loginPhone = () => {
    setErr("");
    if (!phoneOk) return setErr("请输入正确的 11 位手机号");
    if (!sentCode) return setErr("请先获取验证码");
    if (code.trim() !== sentCode) return setErr("验证码不正确");

    const users = loadUsers();
    const first = !users[phone];
    const name = users[phone]?.name || maskPhone(phone);
    if (first) {
      users[phone] = { name };
      saveUsers(users);
    }
    onAuth({ name, account: phone, via: "phone" });
  };

  // 微信一键登录（演示：本机模拟授权）
  const loginWechat = () => {
    const users = loadUsers();
    let acc = Object.keys(users).find((k) => k.startsWith("wx_"));
    if (!acc) {
      acc = "wx_" + Math.random().toString(36).slice(2, 8);
      users[acc] = { name: "微信用户" };
      saveUsers(users);
    }
    onAuth({ name: users[acc].name || "微信用户", account: acc, via: "wechat" });
  };

  return (
    <div className="h-full w-full bg-background text-foreground flex justify-center overflow-hidden">
      <div className="relative w-full max-w-[460px] h-full bg-card border-x border-border flex flex-col overflow-auto">
        {/* Brand */}
        <div className="px-6 pt-16 pb-8">
          <div className="size-14 bg-primary text-primary-foreground flex items-center justify-center rounded-[16px]">
            <Logo className="size-8" />
          </div>
          <h1 className="font-serif font-black text-3xl leading-tight mt-6">闪创工厂</h1>
          <p className="font-mono text-[11px] text-muted-foreground tracking-widest mt-2">
            AI SHORT-VIDEO SCRIPT STUDIO
          </p>
        </div>

        <div className="px-6 flex-1">
          {/* 登录方式切换 tab */}
          <div className="flex bg-secondary rounded-full p-1 mb-6">
            {(
              [
                ["wechat", "一键登录"],
                ["phone", "手机号登录"],
              ] as const
            ).map(([k, label]) => (
              <button
                key={k}
                type="button"
                onClick={() => {
                  setMethod(k);
                  setErr("");
                }}
                className={[
                  "flex-1 rounded-full py-2 text-sm font-bold transition-colors",
                  method === k
                    ? "bg-card text-foreground shadow-sm"
                    : "text-muted-foreground",
                ].join(" ")}
              >
                {label}
              </button>
            ))}
          </div>

          {method === "wechat" && (
            <div className="animate-[fade_.2s_ease] pt-6">
              <button
                type="button"
                onClick={loginWechat}
                className="w-full rounded-full py-3.5 font-bold text-[15px] text-white flex items-center justify-center gap-2 active:scale-95 transition-transform shadow-lg"
                style={{ background: "#07c160", boxShadow: "0 10px 24px rgba(7,193,96,.28)" }}
              >
                <WechatIcon />
                一键登录
              </button>
              <p className="text-center text-[11px] text-muted-foreground mt-2">
                推荐 · 最快捷的方式
              </p>
            </div>
          )}

          {method === "phone" && (
          <div className="animate-[fade_.2s_ease]">
          {/* 手机号验证码登录（首登即注册） */}
          <label className="block mb-3">
            <span className="font-mono text-xs text-muted-foreground mb-1.5 block">手机号</span>
            <input
              value={phone}
              onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 11))}
              type="tel"
              inputMode="numeric"
              placeholder="11 位手机号"
              className="auth-input"
            />
          </label>

          <label className="block">
            <span className="font-mono text-xs text-muted-foreground mb-1.5 block">验证码</span>
            <div className="flex gap-2">
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                type="tel"
                inputMode="numeric"
                placeholder="6 位验证码"
                onKeyDown={(e) => e.key === "Enter" && loginPhone()}
                className="auth-input flex-1"
              />
              <button
                type="button"
                onClick={sendCode}
                disabled={left > 0}
                className={[
                  "shrink-0 px-4 rounded-[14px] text-sm font-bold border transition-colors",
                  left > 0
                    ? "border-border text-muted-foreground/60"
                    : "border-accent text-accent active:scale-95",
                ].join(" ")}
              >
                {left > 0 ? `${left}s` : "获取验证码"}
              </button>
            </div>
          </label>

          {sentCode && left > 0 && (
            <p className="text-[11px] text-accent mt-2">
              演示验证码：<span className="font-mono font-bold tabular-nums">{sentCode}</span>
              （无真实短信服务）
            </p>
          )}
          {err && <p className="text-sm text-primary mt-2 font-medium">{err}</p>}

          <button
            type="button"
            onClick={loginPhone}
            className="w-full mt-5 rounded-full py-3.5 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
          >
            登录 / 注册
          </button>
          <p className="text-center text-[11px] text-muted-foreground mt-2">
            未注册的手机号将自动创建账号
          </p>
          </div>
          )}
        </div>

        <p className="text-center text-[11px] text-muted-foreground/70 py-6 px-6 leading-relaxed">
          登录数据仅保存在本机浏览器，用于演示登录流程。
        </p>
      </div>

      <style>{`
        .auth-input {
          width: 100%;
          background: var(--background);
          border: 1px solid var(--border);
          border-radius: 14px;
          padding: 12px 14px;
          font-size: 15px;
          outline: none;
        }
        .auth-input:focus {
          border-color: var(--accent);
          box-shadow: 0 0 0 3px rgba(63, 60, 168, 0.15);
        }
      `}</style>
    </div>
  );
}

function WechatIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M9.06 3C4.98 3 1.7 5.7 1.7 9.03c0 1.9 1.08 3.6 2.77 4.75l-.55 1.9 2.2-1.14c.63.16 1.28.26 1.94.29-.1-.42-.15-.86-.15-1.3 0-3.2 3.02-5.7 6.75-5.7.24 0 .48.01.72.04C14.9 4.9 12.24 3 9.06 3Zm-2.4 3.2a.93.93 0 1 1 0 1.86.93.93 0 0 1 0-1.86Zm4.9 0a.93.93 0 1 1 0 1.86.93.93 0 0 1 0-1.86Z" />
      <path d="M22.3 13.43c0-2.8-2.75-5.06-6.14-5.06-3.46 0-6.14 2.27-6.14 5.06 0 2.8 2.68 5.06 6.14 5.06.72 0 1.42-.1 2.06-.29l1.87.97-.5-1.62c1.6-.98 2.71-2.46 2.71-4.12Zm-8-1.2a.78.78 0 1 1 0-1.55.78.78 0 0 1 0 1.55Zm3.86 0a.78.78 0 1 1 0-1.55.78.78 0 0 1 0 1.55Z" />
    </svg>
  );
}

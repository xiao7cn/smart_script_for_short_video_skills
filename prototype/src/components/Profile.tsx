import { useState } from "react";
import type { User } from "./Auth";
import Persona from "./Persona";
import type { PersonaData } from "../data/config";

type Props = {
  user: User;
  stats: { total: number; generated: number };
  credits: number;
  persona: PersonaData;
  onPersonaChange: (p: PersonaData) => void;
  onPersonaReset: () => void;
  onRecharge: (add: number, label: string) => void;
  onLogout: () => void;
};

// 充值套餐：金额越大，赠送比例越高
type Pack = { price: number; base: number; bonus: number };
const PACKS: Pack[] = [
  { price: 19, base: 30, bonus: 0 },
  { price: 59, base: 100, bonus: 15 },
  { price: 149, base: 300, bonus: 60 },
  { price: 399, base: 1000, bonus: 300 },
];

export default function Profile({
  user,
  stats,
  credits,
  persona,
  onPersonaChange,
  onPersonaReset,
  onRecharge,
  onLogout,
}: Props) {
  const [showRecharge, setShowRecharge] = useState(false);
  const [showPersona, setShowPersona] = useState(false);
  const initial = (user.name || user.account).slice(0, 1).toUpperCase();
  const isWechat = user.via === "wechat";

  if (showPersona) {
    return (
      <Persona
        persona={persona}
        onChange={onPersonaChange}
        onReset={onPersonaReset}
        onBack={() => setShowPersona(false)}
      />
    );
  }

  return (
    <div className="pb-10">
      {/* Identity card —— 微信用户蓝底 */}
      <div className="px-4 pt-6">
        <div
          className="rounded-[20px] text-white p-5 flex items-center gap-4"
          style={{ background: isWechat ? "#1677ff" : "var(--foreground)" }}
        >
          <div className="size-14 rounded-[16px] bg-white/15 flex items-center justify-center font-serif font-black text-2xl">
            {initial}
          </div>
          <div className="min-w-0">
            <p className="font-serif font-black text-xl truncate">{user.name}</p>
            <p className="font-mono text-[11px] text-white/70 truncate mt-0.5">
              {isWechat ? "微信登录" : "手机号登录"} · {user.account}
            </p>
          </div>
        </div>
      </div>

      {/* 剩余额度 */}
      <div className="px-4 mt-3">
        <div className="rounded-[16px] border border-accent/30 bg-accent/5 p-4 flex items-center justify-between">
          <div>
            <p className="font-mono text-xs text-accent">剩余额度</p>
            <p className="mt-1">
              <span className="font-serif font-black text-3xl tabular-nums text-accent">
                {credits}
              </span>
              <span className="text-sm text-muted-foreground ml-1">条</span>
            </p>
            <p className="text-[11px] text-muted-foreground mt-1">每生成一条口播文案消耗 1 条额度</p>
          </div>
          <button
            type="button"
            onClick={() => setShowRecharge(true)}
            className="shrink-0 rounded-full px-5 py-2.5 font-bold text-sm bg-accent text-accent-foreground shadow-lg shadow-accent/30 active:scale-95 transition-transform"
          >
            充值
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="px-4 mt-3 grid grid-cols-2 gap-3">
        <Stat label="文案总数" value={stats.total} />
        <Stat label="本机生成" value={stats.generated} />
      </div>

      {/* Menu */}
      <div className="px-4 mt-3">
        <div className="rounded-[16px] border border-border bg-card overflow-hidden divide-y divide-border">
          <Row
            title="人设档案"
            desc={`${persona.model || "未命名"} · 生成时自动注入`}
            onClick={() => setShowPersona(true)}
          />
          <Row title="账号与安全" desc={`账号：${user.account}`} />
          <Row title="偏好设置" desc="生成配比、默认脚本类型" />
          <Row title="关于闪创工厂" desc="AI 短视频口播文案 · H5 版" />
        </div>
      </div>

      <div className="px-4 mt-6">
        <button
          type="button"
          onClick={onLogout}
          className="w-full rounded-full py-3.5 font-bold text-sm border-2 border-primary text-primary active:scale-95 transition-transform"
        >
          退出登录
        </button>
      </div>

      {showRecharge && (
        <RechargeModal
          onClose={() => setShowRecharge(false)}
          onConfirm={(p) => {
            setShowRecharge(false);
            const total = p.base + p.bonus;
            onRecharge(total, `${p.base} 条${p.bonus ? ` + 赠 ${p.bonus} 条` : ""}`);
          }}
        />
      )}
    </div>
  );
}

function RechargeModal({
  onClose,
  onConfirm,
}: {
  onClose: () => void;
  onConfirm: (p: Pack) => void;
}) {
  const [sel, setSel] = useState(1);
  const pack = PACKS[sel];

  return (
    <div
      className="absolute inset-0 z-40 flex items-end justify-center bg-black/40 animate-[fade_.2s_ease]"
      onClick={onClose}
    >
      <div
        className="w-full bg-card rounded-t-[24px] p-5 pb-7 animate-[slideup_.28s_cubic-bezier(.2,.8,.2,1)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mx-auto w-10 h-1 rounded-full bg-border mb-4" />
        <h3 className="font-serif font-black text-xl text-center">额度充值</h3>
        <p className="text-xs text-muted-foreground text-center mt-1">充值越多，赠送越多</p>

        <div className="grid grid-cols-2 gap-2.5 mt-5">
          {PACKS.map((p, i) => {
            const active = sel === i;
            return (
              <button
                key={i}
                type="button"
                onClick={() => setSel(i)}
                className={[
                  "relative text-left p-3.5 rounded-[16px] border-2 transition-all active:scale-[0.98]",
                  active ? "border-accent bg-accent/5" : "border-border bg-card",
                ].join(" ")}
              >
                {p.bonus > 0 && (
                  <span className="absolute -top-2 right-3 font-mono text-[10px] px-1.5 py-0.5 rounded-full bg-accent text-accent-foreground">
                    送 {p.bonus}
                  </span>
                )}
                <div className="flex items-baseline gap-1">
                  <span className="font-serif font-black text-2xl tabular-nums">{p.base}</span>
                  <span className="text-xs text-muted-foreground">条</span>
                </div>
                {p.bonus > 0 && (
                  <p className="text-[11px] text-accent mt-0.5">实得 {p.base + p.bonus} 条</p>
                )}
                <p className="font-mono text-sm font-bold mt-1.5">￥{p.price}</p>
              </button>
            );
          })}
        </div>

        <button
          type="button"
          onClick={() => onConfirm(pack)}
          className="w-full mt-6 rounded-full py-3.5 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
        >
          支付 ￥{pack.price} · 到账 {pack.base + pack.bonus} 条
        </button>
        <button
          type="button"
          onClick={onClose}
          className="w-full mt-2 text-center text-sm text-muted-foreground py-1.5"
        >
          取消
        </button>
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-[16px] border border-border bg-card p-4">
      <span className="font-serif font-black text-3xl tabular-nums text-primary">{value}</span>
      <p className="text-xs text-muted-foreground mt-1">{label}</p>
    </div>
  );
}

function Row({ title, desc, onClick }: { title: string; desc: string; onClick?: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      className="w-full text-left flex items-center gap-3 px-4 py-3.5 active:bg-secondary/50 disabled:active:bg-transparent transition-colors"
    >
      <div className="min-w-0 flex-1">
        <p className="font-serif font-bold text-[15px]">{title}</p>
        <p className="text-xs text-muted-foreground mt-0.5 truncate">{desc}</p>
      </div>
      <span className="text-muted-foreground/50 text-lg shrink-0">›</span>
    </button>
  );
}

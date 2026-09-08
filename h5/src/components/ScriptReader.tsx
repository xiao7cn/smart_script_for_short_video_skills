import { useEffect, useState } from "react";
import { errMsg } from "../api/client";
import { getScript } from "../api/script";
import type { ScriptBreakdown, ScriptDetail, ScriptListItem } from "../api/types";
import { useOptions } from "../hooks/useOptions";
import { colorOf } from "../lib/palette";

export default function ScriptReader({
  item,
  onClose,
  fmt,
  onToast,
}: {
  item: ScriptListItem;
  onClose: () => void;
  fmt: (ts: string) => string;
  onToast: (msg: string) => void;
}) {
  const { scriptTypes } = useOptions();
  const [detail, setDetail] = useState<ScriptDetail | null>(null);
  const [copied, setCopied] = useState(false);
  const [showBreakdown, setShowBreakdown] = useState(false);
  const c = colorOf(item.scriptType);

  // 列表接口不返回正文，进详情页再拉一次
  useEffect(() => {
    let alive = true;
    void getScript(item.id)
      .then((d) => {
        if (alive) setDetail(d);
      })
      .catch((e) => {
        if (alive) onToast(errMsg(e));
      });
    return () => {
      alive = false;
    };
  }, [item.id]);

  const body = detail?.body ?? "";
  // 结构公式先用脚本类型推出来，正文到了再以详情为准，避免加载时行高跳动
  const structure =
    detail?.structure ?? scriptTypes.find((s) => s.key === item.scriptType)?.formula ?? "";
  const breakdown = detail?.breakdown ?? null;

  const copy = async () => {
    if (!detail) return;
    try {
      await navigator.clipboard.writeText(`${item.title}\n\n${detail.body}`);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="absolute inset-0 z-30 bg-background flex flex-col animate-[fade_.2s_ease]">
      <header className="flex items-center gap-3 px-4 h-14 border-b border-border shrink-0">
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-accent font-medium active:opacity-60"
        >
          ← 返回
        </button>
        <span className="font-mono text-xs text-muted-foreground ml-auto">
          NO.{String(item.seqNo).padStart(2, "0")}
        </span>
      </header>

      <div className="flex-1 overflow-auto">
        <div className="px-5 pt-5 pb-3">
          <div className="flex items-center gap-2">
            <span
              className="font-mono text-[11px] px-2 py-0.5 rounded-full font-bold"
              style={{ background: c + "1a", color: c }}
            >
              {item.scriptType}
            </span>
            {item.generated && (
              <span className="font-mono text-[11px] px-2 py-0.5 rounded-full bg-highlight/25 text-highlight-foreground">
                本机生成
              </span>
            )}
          </div>
          <h1 className="font-serif font-black text-xl leading-snug mt-3">{item.title}</h1>

          <div className="mt-4 rounded-[16px] bg-muted/60 divide-y divide-border/70 text-[13px] overflow-hidden">
            <Row label="选题" value={item.topic} />
            <Row label="结构" value={structure} />
            <Row label="参数" value={`${item.topicType} ｜ ${item.source}`} />
            <Row label="宫格" value={`${item.grid} ＋ ${item.element}`} />
            <Row label="生成时间" value={fmt(item.createdAt)} />
          </div>

          {detail?.needsMaterial && (
            <div
              className="mt-3 rounded-[12px] pl-3 pr-3 py-2.5 text-[12px] leading-relaxed"
              style={{ background: "rgba(63,60,168,0.08)", color: "#3f3ca8" }}
            >
              <span className="font-bold">需替换真实素材：</span>
              {detail.needsMaterial}
            </div>
          )}

          {item.hasBreakdown && (
            <button
              type="button"
              onClick={() => setShowBreakdown(true)}
              className="mt-3 w-full flex items-center gap-3 rounded-[14px] border border-accent/40 bg-accent/5 px-3.5 py-3 text-left active:scale-[0.99] transition-transform"
            >
              <span className="size-8 rounded-[10px] bg-accent text-accent-foreground flex items-center justify-center text-base shrink-0">
                ⚡
              </span>
              <span className="flex-1 leading-tight">
                <span className="font-serif font-bold text-sm block">原文案 + 爆款拆解</span>
                <span className="text-[11px] text-muted-foreground">
                  这条由对标爆款拆解重写，看原文与拆解逻辑
                </span>
              </span>
              <span className="text-accent text-lg shrink-0">→</span>
            </button>
          )}
        </div>

        <div className="px-5 pb-28 pt-2">
          <div className="flex items-baseline justify-between mb-2">
            <span className="font-mono text-xs text-muted-foreground">正文 · 口播稿</span>
            <span className="font-mono text-xs text-muted-foreground">约 {item.words} 字</span>
          </div>
          <article className="font-serif text-[15px] leading-[1.95] text-foreground/90 space-y-4">
            {body.split("\n\n").map((p, i) => (
              <p key={i}>{p}</p>
            ))}
          </article>
        </div>
      </div>

      <div className="shrink-0 border-t border-border p-3 bg-background">
        <button
          type="button"
          onClick={copy}
          className="w-full bg-primary text-primary-foreground text-sm font-bold py-3.5 rounded-full shadow-lg shadow-primary/30 active:scale-95 transition-transform"
        >
          {copied ? "已复制到剪贴板 ✓" : "复制标题 + 口播文案"}
        </button>
      </div>

      {breakdown && showBreakdown && (
        <BreakdownView breakdown={breakdown} onClose={() => setShowBreakdown(false)} />
      )}
    </div>
  );
}

function BreakdownView({
  breakdown,
  onClose,
}: {
  breakdown: ScriptBreakdown;
  onClose: () => void;
}) {
  return (
    <div className="absolute inset-0 z-40 bg-background flex flex-col animate-[fade_.2s_ease]">
      <header className="flex items-center gap-3 px-4 h-14 border-b border-border shrink-0">
        <button
          type="button"
          onClick={onClose}
          className="text-sm text-accent font-medium active:opacity-60"
        >
          ← 返回文案
        </button>
        <span className="font-mono text-xs text-muted-foreground ml-auto">对标爆款拆解</span>
      </header>

      <div className="flex-1 overflow-auto px-5 py-5 space-y-6">
        {/* 对标来源 */}
        <section>
          <p className="font-mono text-[11px] tracking-widest text-primary mb-2">对标来源</p>
          {breakdown.autoSearch && (
            <div className="flex items-center gap-2 text-[13px] text-foreground/85 mb-2">
              <span className="size-6 rounded-[8px] bg-accent/10 text-accent flex items-center justify-center text-xs shrink-0">
                ⚡
              </span>
              由系统自动检索的同选题高赞爆款
            </div>
          )}
          {breakdown.refs?.length ? (
            <ul className="space-y-1.5">
              {breakdown.refs.map((r, i) => (
                <li
                  key={i}
                  className="text-[12px] text-accent break-all bg-card border border-border rounded-[10px] px-3 py-2"
                >
                  {r}
                </li>
              ))}
            </ul>
          ) : (
            !breakdown.autoSearch && (
              <p className="text-[12px] text-muted-foreground">未附对标链接</p>
            )
          )}
        </section>

        {/* 原文案 */}
        <section>
          <p className="font-mono text-[11px] tracking-widest text-primary mb-2">原文案 · 爆款口播</p>
          <article className="rounded-[16px] bg-muted/60 p-4 font-serif text-[14px] leading-[1.9] text-foreground/85 space-y-2.5">
            {breakdown.original.split("\n").map((p, i) => (
              <p key={i}>{p}</p>
            ))}
          </article>
        </section>

        {/* 爆款拆解 */}
        <section>
          <p className="font-mono text-[11px] tracking-widest text-primary mb-2">爆款拆解 · 它为什么火</p>
          <div className="space-y-2.5">
            {breakdown.points.map((pt, i) => (
              <div key={i} className="rounded-[14px] border border-border bg-card p-3.5">
                <div className="flex items-center gap-2 mb-1">
                  <span className="font-mono text-[10px] px-1.5 py-0.5 rounded-full bg-accent/10 text-accent font-bold">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="font-serif font-bold text-sm">{pt.label}</span>
                </div>
                <p className="text-[12px] text-muted-foreground leading-relaxed">{pt.detail}</p>
              </div>
            ))}
          </div>
        </section>

        {/* 二次创作 */}
        <section className="pb-6">
          <p className="font-mono text-[11px] tracking-widest text-primary mb-2">
            二次创作 · 我们改了什么
          </p>
          <div
            className="rounded-[16px] p-4 text-[13px] leading-[1.9] space-y-2"
            style={{ background: "rgba(63,60,168,0.08)", color: "#3f3ca8" }}
          >
            {breakdown.rewrite.split("\n").map((p, i) => (
              <p key={i}>{p}</p>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3 px-3.5 py-2.5">
      <span className="font-mono text-xs text-muted-foreground shrink-0 w-14 pt-0.5">{label}</span>
      <span className="text-foreground/85 leading-relaxed">{value}</span>
    </div>
  );
}

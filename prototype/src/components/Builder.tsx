import { useMemo, useState } from "react";
import Chip from "./Chip";
import {
  topicTypes,
  topicSources,
  gridInner,
  gridMiddle,
  gridOuter,
  viralElements,
  scriptTypes,
  deaiPrompt,
  type PersonaData,
} from "../data/config";
import type { WizardSel } from "../lib/generate";
import { colorOf } from "../lib/palette";

type Props = {
  persona: PersonaData;
  onGenerate: (sel: WizardSel, count: number) => void;
};

// 支持多选的字段（值为数组）
type ArrKey = "topicType" | "source" | "inner" | "middle" | "outer" | "element" | "scriptType";
type StepId = "topicType" | "source" | "refs" | "grid" | "element" | "scriptType";

const REF_SOURCES = ["对标爆款拆解", "评论私信需求挖掘"];
const STEP_TITLE: Record<StepId, string> = {
  topicType: "选题类型",
  source: "选题来源",
  refs: "对标视频",
  grid: "25 宫格",
  element: "爆款元素",
  scriptType: "脚本类型",
};
// 「跳过」时需清空的字段
const SKIP_ARR_KEYS: Partial<Record<StepId, ArrKey[]>> = {
  topicType: ["topicType"],
  source: ["source"],
  grid: ["inner", "middle", "outer"],
  element: ["element"],
  scriptType: ["scriptType"],
};

const join = (a?: string[]) => (a ?? []).join("、");

export default function Builder({ persona, onGenerate }: Props) {
  const [step, setStep] = useState(0);
  const [sel, setSel] = useState<WizardSel>({});
  const [showCount, setShowCount] = useState(false);
  const [askSkip, setAskSkip] = useState(false); // 未选任何项时的「是否跳过」确认弹窗
  const [promptDraft, setPromptDraft] = useState<string | null>(null); // 用户手改后的提示词

  const needsRefs = (sel.source ?? []).some((s) => REF_SOURCES.includes(s));

  // 动态步骤流：选中对标类来源时插入「对标视频」页
  const flow: StepId[] = [
    "topicType",
    "source",
    ...(needsRefs ? (["refs"] as StepId[]) : []),
    "grid",
    "element",
    "scriptType",
  ];
  const TOTAL = flow.length;
  const isReview = step >= TOTAL;
  const current = flow[step];

  const has = (k: ArrKey, v: string) => (sel[k] ?? []).includes(v);
  const toggle = (k: ArrKey, v: string) =>
    setSel((s) => {
      const cur = s[k] ?? [];
      return { ...s, [k]: cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v] };
    });

  const selScripts = sel.scriptType ?? [];
  const st = selScripts.length === 1 ? scriptTypes.find((s) => s.key === selScripts[0]) : undefined;
  const gridPath = [sel.inner, sel.middle, sel.outer]
    .map((a) => (a ?? []).join("/"))
    .filter(Boolean)
    .join(" × ");

  const kick = (label: string) => `STEP ${String(step + 1).padStart(2, "0")} · ${label}`;

  const next = () => setStep((s) => Math.min(s + 1, TOTAL));
  // 当前步骤是否已有任何选择
  const hasSelection = (id: StepId) => {
    switch (id) {
      case "topicType":
        return (sel.topicType?.length ?? 0) > 0;
      case "source":
        return (sel.source?.length ?? 0) > 0;
      case "refs":
        return !!sel.autoSearch || (sel.refs?.filter((r) => r.trim()).length ?? 0) > 0;
      case "grid":
        return (
          (sel.inner?.length ?? 0) + (sel.middle?.length ?? 0) + (sel.outer?.length ?? 0) > 0
        );
      case "element":
        return (sel.element?.length ?? 0) > 0;
      case "scriptType":
        return (sel.scriptType?.length ?? 0) > 0;
    }
  };
  // 「继续细化」：未勾选任何选项时，询问是否跳过
  const refine = () => {
    if (!isReview && !hasSelection(current)) {
      setAskSkip(true);
      return;
    }
    next();
  };
  const back = () => setStep((s) => Math.max(s - 1, 0));
  const skip = () => {
    if (!isReview) {
      const keys = SKIP_ARR_KEYS[current];
      setSel((s) => {
        const cleared = { ...s };
        keys?.forEach((k) => delete cleared[k]);
        if (current === "refs") {
          delete cleared.refs;
          delete cleared.autoSearch;
        }
        return cleared;
      });
    }
    next();
  };

  const prompt = useMemo(() => {
    return [
      "你现在是一名资深的短视频文案写手，写文案前会搜罗分析同选题的高赞视频做参考，再按脚本结构撰写口播文案。全程口语化，多用「我」和「你」，一句话只讲一件事，写完能读出声、你奶奶也听得懂。只要纯文案，不要画面，不低于 500 字。",
      "",
      "【人设】" + persona.model + "｜" + persona.identity,
      "【价值定位】" + persona.value,
      "【目标人群】" + persona.audience,
      "【表达风格】" + persona.tone,
      "",
      "【选题类型】" + (sel.topicType?.length ? join(sel.topicType) + "类选题" : "不限"),
      "【选题来源】" + (sel.source?.length ? join(sel.source) : "不限"),
      ...(sel.refs?.filter((r) => r.trim()).length
        ? ["【参考链接】" + sel.refs.filter((r) => r.trim()).join("  ")]
        : []),
      ...(sel.autoSearch ? ["【自动搜索】开启：优先检索同选题高赞爆款视频作为对标参考。"] : []),
      "【25 宫格配对】" + (gridPath || "不限"),
      "【爆款元素】" + (sel.element?.length ? join(sel.element) : "不限"),
      "【选题方向】" + (sel.topicDraft?.trim() || "由你结合以上参数拟定"),
      "",
      "【脚本类型】" + (sel.scriptType?.length ? join(sel.scriptType) : "按 4:1:3:2 配比随机"),
      "【结构公式】" + (st ? st.formula : "按所选脚本类型对应结构（多选则逐条轮换）"),
      "",
      "【硬性红线】通篇避开这些词：" + persona.banned.join("、") + "。",
      "先给我 3 个吸睛标题（标题≠选题，只为让人停留），再按结构公式输出正文。",
    ].join("\n");
  }, [sel, gridPath, st, persona]);

  const shownPrompt = promptDraft ?? prompt;

  return (
    <div className="flex flex-col min-h-full">
      {/* Progress header */}
      <div className="px-4 pt-4 pb-3 sticky top-0 bg-background/90 backdrop-blur z-10">
        <div className="flex items-center gap-1.5">
          {flow.map((id, i) => (
            <div
              key={id}
              className="h-1.5 flex-1 rounded-full transition-colors"
              style={{
                background:
                  i < step
                    ? "#3f3ca8"
                    : i === step
                      ? "rgba(63,60,168,0.4)"
                      : "rgba(26,26,30,0.1)",
              }}
            />
          ))}
          <div
            className="h-1.5 flex-1 rounded-full transition-colors"
            style={{ background: isReview ? "#3f3ca8" : "rgba(26,26,30,0.1)" }}
          />
        </div>
        <div className="flex items-center justify-between mt-2">
          <span className="font-mono text-[11px] text-muted-foreground">
            {isReview ? "收尾 · 确认生成" : `第 ${step + 1} / ${TOTAL} 步 · ${STEP_TITLE[current]}`}
          </span>
          {step > 0 && (
            <button
              type="button"
              onClick={back}
              className="text-xs text-accent font-medium active:opacity-60"
            >
              ← 上一步
            </button>
          )}
        </div>
      </div>

      {/* Step body */}
      <div className="flex-1 px-4 pb-40">
        {current === "topicType" && (
          <StepIntro
            kicker={kick("战略意图")}
            title="这条内容，你想干嘛？"
            sub="可多选、可不选、也可跳过 —— 多选时按批次轮流出稿。"
          >
            <div className="flex flex-col gap-2.5">
              {topicTypes.map((t) => (
                <CardOption
                  key={t.key}
                  active={has("topicType", t.key)}
                  color={colorOf(t.key)}
                  onClick={() => toggle("topicType", t.key)}
                  title={t.name}
                  tag={t.tag}
                  desc={t.desc}
                />
              ))}
            </div>
          </StepIntro>
        )}

        {current === "source" && (
          <StepIntro
            kicker={kick("灵感来源")}
            title="真实需求从哪儿挖？"
            sub="可多选。选到「对标 / 评论」类来源，下一步会进入对标视频添加页。"
          >
            <div className="flex flex-col gap-2.5">
              {topicSources.map((s) => (
                <CardOption
                  key={s.key}
                  active={has("source", s.key)}
                  color={colorOf("破圈类")}
                  onClick={() => toggle("source", s.key)}
                  title={s.key}
                  tag={s.ready ? undefined : "需补素材"}
                  desc={s.desc}
                  note={s.note}
                />
              ))}
            </div>
          </StepIntro>
        )}

        {current === "refs" && (
          <StepIntro
            kicker={kick("对标视频")}
            title="添加对标视频链接"
            sub="贴上想拆解的爆款视频 / 帖子链接，可加多条；也可让系统自动搜。"
          >
            <button
              type="button"
              onClick={() => setSel((s) => ({ ...s, autoSearch: !s.autoSearch }))}
              className={[
                "w-full flex items-center gap-3 p-3.5 rounded-[16px] border-2 transition-all active:scale-[0.99] mb-4",
                sel.autoSearch
                  ? "border-accent bg-accent/5"
                  : "border-dashed border-border bg-card",
              ].join(" ")}
            >
              <span
                className="size-9 rounded-[12px] flex items-center justify-center text-lg shrink-0"
                style={{
                  background: sel.autoSearch ? "#3f3ca8" : "rgba(26,26,30,0.06)",
                  color: sel.autoSearch ? "#fff" : "var(--muted-foreground)",
                }}
              >
                ⚡
              </span>
              <span className="text-left flex-1">
                <span className="font-serif font-bold text-[15px] block">自动搜索爆款视频</span>
                <span className="text-[11px] text-muted-foreground">
                  由系统按你的选题自动检索高赞对标，无需手动粘贴
                </span>
              </span>
              <span
                className={[
                  "shrink-0 size-6 rounded-full border-2 flex items-center justify-center text-xs font-bold",
                  sel.autoSearch ? "border-accent bg-accent text-accent-foreground" : "border-border",
                ].join(" ")}
              >
                {sel.autoSearch ? "✓" : ""}
              </span>
            </button>

            <div className="flex items-center gap-3 my-3">
              <span className="h-px flex-1 bg-border" />
              <span className="font-mono text-[11px] text-muted-foreground">或手动添加链接</span>
              <span className="h-px flex-1 bg-border" />
            </div>

            <RefList items={sel.refs ?? []} onChange={(refs) => setSel((s) => ({ ...s, refs }))} />

            {(sel.refs?.filter((r) => r.trim()).length ?? 0) > 0 && (
              <p className="text-[11px] text-muted-foreground mt-3 leading-relaxed">
                已添加 {sel.refs?.filter((r) => r.trim()).length} 条对标链接，将用于差异化二次创作。
              </p>
            )}
          </StepIntro>
        )}

        {current === "grid" && (
          <StepIntro
            kicker={kick("玩转宫格")}
            title="内圈 × 中圈 × 外圈"
            sub="三层都可多选、可不选，随手配对就能引出话题。"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="font-mono text-xs text-muted-foreground">当前配对</span>
              <button
                type="button"
                onClick={() => setSel((s) => ({ ...s, inner: [], middle: [], outer: [] }))}
                className="text-xs text-primary font-medium active:opacity-60"
              >
                清空
              </button>
            </div>
            <div
              className="rounded-[12px] px-3 py-2.5 mb-4 font-serif font-bold text-sm"
              style={{ background: "rgba(63,60,168,0.10)", color: "#3f3ca8" }}
            >
              {gridPath || "尚未配对（可跳过）"}
            </div>

            <p className="font-mono text-xs text-muted-foreground mb-2">内圈 · 领域（可选）</p>
            <div className="flex flex-wrap gap-2 mb-4">
              {gridInner.map((m) => (
                <Chip key={m} active={has("inner", m)} onClick={() => toggle("inner", m)}>
                  {m}
                </Chip>
              ))}
            </div>
            <p className="font-mono text-xs text-muted-foreground mb-2">中圈 · 话题（可多选）</p>
            <div className="flex flex-wrap gap-2 mb-4">
              {gridMiddle.map((m) => (
                <Chip key={m} active={has("middle", m)} onClick={() => toggle("middle", m)}>
                  {m}
                </Chip>
              ))}
            </div>
            <p className="font-mono text-xs text-muted-foreground mb-2">外圈 · 人群 / 维度（可多选）</p>
            <div className="flex flex-wrap gap-2">
              {gridOuter.map((o) => (
                <Chip key={o} active={has("outer", o)} onClick={() => toggle("outer", o)}>
                  {o}
                </Chip>
              ))}
            </div>
          </StepIntro>
        )}

        {current === "element" && (
          <StepIntro
            kicker={kick("加钩子")}
            title="套一个爆款元素"
            sub="可多选，给平平的话题加一层抓人的味道，也可留空。"
          >
            <div className="grid grid-cols-2 gap-2.5">
              {viralElements.map((e) => (
                <button
                  key={e.key}
                  type="button"
                  onClick={() => toggle("element", e.key)}
                  className={[
                    "text-left p-3 rounded-[14px] border transition-all active:scale-[0.98]",
                    has("element", e.key)
                      ? "border-transparent shadow-sm"
                      : "border-border bg-card hover:border-primary/40",
                  ].join(" ")}
                  style={has("element", e.key) ? { background: "#3f3ca8", color: "#fff" } : undefined}
                >
                  <span className="font-serif font-bold text-sm">{e.key}</span>
                  <p
                    className={[
                      "text-[11px] mt-1 leading-snug",
                      has("element", e.key) ? "text-white/85" : "text-muted-foreground",
                    ].join(" ")}
                  >
                    {e.hint}
                  </p>
                </button>
              ))}
            </div>
          </StepIntro>
        )}

        {current === "scriptType" && (
          <StepIntro
            kicker={kick("成稿骨架")}
            title="用哪套脚本结构？"
            sub="可多选，批次内轮流套用；不选则按 4:1:3:2 的黄金配比随机分配。"
          >
            <div className="flex flex-col gap-2.5">
              {scriptTypes.map((s) => (
                <CardOption
                  key={s.key}
                  active={has("scriptType", s.key)}
                  color={colorOf(s.key)}
                  onClick={() => toggle("scriptType", s.key)}
                  title={s.key}
                  tag={`权重 ${s.ratio}｜${s.goal}`}
                  formula={s.formula}
                  desc={s.desc}
                />
              ))}
            </div>
          </StepIntro>
        )}

        {isReview && (
          <StepIntro
            kicker={`STEP ${String(TOTAL + 1).padStart(2, "0")} · 确认`}
            title="给这批文案定个调"
            sub="补一句选题方向（可选），或直接生成 —— 空着的参数会自动补齐。"
          >
            <textarea
              value={sel.topicDraft ?? ""}
              onChange={(e) => setSel((s) => ({ ...s, topicDraft: e.target.value }))}
              rows={3}
              placeholder={
                gridPath
                  ? `例：${sel.middle?.[0] || "AI就业"}这块，${sel.outer?.[0] || "普通人"}最容易踩的坑是什么`
                  : "一句话说清这批内容大致要聊什么（留空则由参数自动拟定）"
              }
              className="w-full text-sm border border-border bg-card p-3 rounded-[12px] resize-none focus:outline-none focus:border-accent focus:ring-2 focus:ring-ring/30 leading-relaxed"
            />

            <div className="mt-4 rounded-[16px] bg-foreground text-background overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
                <span className="font-serif font-bold text-sm">
                  配方 · 提示词{promptDraft !== null ? "（已手改）" : "预览"}
                </span>
                <div className="flex items-center gap-1.5">
                  {promptDraft !== null && (
                    <button
                      type="button"
                      onClick={() => setPromptDraft(null)}
                      className="text-[11px] font-mono px-2 py-1 rounded-md bg-white/10 hover:bg-white/20 transition-colors"
                    >
                      重置
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() =>
                      navigator.clipboard?.writeText(
                        shownPrompt + "\n\n---\n去 AI 味二次改写：\n" + deaiPrompt,
                      )
                    }
                    className="text-[11px] font-mono px-2 py-1 rounded-md bg-white/10 hover:bg-white/20 transition-colors"
                  >
                    复制
                  </button>
                </div>
              </div>
              <textarea
                value={shownPrompt}
                onChange={(e) => setPromptDraft(e.target.value)}
                rows={12}
                spellCheck={false}
                className="w-full bg-transparent px-4 py-3 text-[12px] leading-relaxed whitespace-pre-wrap font-sans text-background/90 max-h-64 overflow-auto no-scrollbar resize-none focus:outline-none"
              />
            </div>
            <p className="text-[11px] text-muted-foreground mt-1.5 leading-relaxed">
              可直接在上方编辑提示词；改动仅影响复制内容，本机批量生成仍按所选参数进行。
            </p>
          </StepIntro>
        )}
      </div>

      {/* Sticky action bar — 第一行：跳过 / 继续细化；第二行：直接生成 */}
      <div className="sticky bottom-0 px-4 py-3 bg-background/95 backdrop-blur border-t border-border space-y-2.5">
        {!isReview && (
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={skip}
              className="flex-1 rounded-full py-3 font-bold text-sm border-2 border-dashed border-border text-muted-foreground active:scale-95 transition-transform"
            >
              跳过
            </button>
            <button
              type="button"
              onClick={refine}
              className="flex-1 rounded-full py-3 font-bold text-sm border-2 border-accent text-accent active:scale-95 transition-transform"
            >
              继续细化 →
            </button>
          </div>
        )}
        <button
          type="button"
          onClick={() => setShowCount(true)}
          className="w-full rounded-full py-3.5 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
        >
          直接生成
        </button>
        {isReview && (
          <button
            type="button"
            onClick={() => setStep(0)}
            className="w-full text-center text-xs text-muted-foreground active:opacity-60"
          >
            重新选一遍
          </button>
        )}
      </div>

      {showCount && (
        <CountModal
          onClose={() => setShowCount(false)}
          onConfirm={(n) => {
            setShowCount(false);
            onGenerate(sel, n);
          }}
        />
      )}

      {askSkip && (
        <ConfirmModal
          title="您没有选择任何选项"
          desc="是需要跳过这一步吗？"
          confirmText="跳过这一步"
          cancelText="返回选择"
          onCancel={() => setAskSkip(false)}
          onConfirm={() => {
            setAskSkip(false);
            skip();
          }}
        />
      )}
    </div>
  );
}

function ConfirmModal({
  title,
  desc,
  confirmText,
  cancelText,
  onConfirm,
  onCancel,
}: {
  title: string;
  desc: string;
  confirmText: string;
  cancelText: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <div
      className="absolute inset-0 z-50 flex items-center justify-center px-8 bg-black/40 animate-[fade_.2s_ease]"
      onClick={onCancel}
    >
      <div
        className="w-full max-w-[320px] bg-card rounded-[22px] p-6 animate-[slideup_.28s_cubic-bezier(.2,.8,.2,1)]"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="font-serif font-black text-lg text-center">{title}</h3>
        <p className="text-sm text-muted-foreground text-center mt-2 leading-relaxed">{desc}</p>
        <div className="mt-6 flex flex-col gap-2.5">
          <button
            type="button"
            onClick={onConfirm}
            className="w-full rounded-full py-3 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
          >
            {confirmText}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="w-full rounded-full py-3 font-bold text-sm border-2 border-border text-muted-foreground active:scale-95 transition-transform"
          >
            {cancelText}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ---------- 子组件 ---------- */

function StepIntro({
  kicker,
  title,
  sub,
  children,
}: {
  kicker: string;
  title: string;
  sub: string;
  children: React.ReactNode;
}) {
  return (
    <div className="animate-[fade_.25s_ease] pt-1">
      <p className="font-mono text-[11px] tracking-widest text-primary">{kicker}</p>
      <h2 className="font-serif font-black text-2xl leading-tight mt-1.5">{title}</h2>
      <p className="text-sm text-muted-foreground mt-1.5 mb-4 leading-relaxed">{sub}</p>
      {children}
    </div>
  );
}

function CardOption({
  active,
  disabled,
  color,
  onClick,
  title,
  tag,
  formula,
  desc,
  note,
}: {
  active: boolean;
  disabled?: boolean;
  color: string;
  onClick: () => void;
  title: string;
  tag?: string;
  formula?: string;
  desc: string;
  note?: string;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className={[
        "text-left p-3.5 rounded-[16px] border transition-all active:scale-[0.99]",
        disabled
          ? "border-dashed border-border opacity-60 cursor-not-allowed"
          : active
            ? "border-transparent shadow-md"
            : "border-border bg-card hover:border-accent/40",
      ].join(" ")}
      style={active && !disabled ? { background: color + "12", borderColor: color } : undefined}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span
            className="size-2.5 rounded-full shrink-0"
            style={{ background: active || !disabled ? color : "#c9c5d6" }}
          />
          <span className="font-serif font-bold text-[15px]">{title}</span>
        </div>
        {tag && (
          <span
            className="font-mono text-[10px] px-1.5 py-0.5 rounded-full"
            style={{ background: color + "1a", color }}
          >
            {tag}
          </span>
        )}
      </div>
      {formula && (
        <p className="font-mono text-[11px] mt-1.5 ml-4.5" style={{ color }}>
          {formula}
        </p>
      )}
      <p className="text-xs text-muted-foreground mt-1.5 ml-4.5 leading-relaxed">{desc}</p>
      {note && <p className="text-[11px] text-primary/80 mt-1 ml-4.5">＊{note}</p>}
    </button>
  );
}

function RefList({
  items,
  onChange,
}: {
  items: string[];
  onChange: (v: string[]) => void;
}) {
  const rows = items.length ? items : [""];
  const setAt = (i: number, v: string) => onChange(rows.map((x, j) => (j === i ? v : x)));
  const removeAt = (i: number) => {
    const next = rows.filter((_, j) => j !== i);
    onChange(next);
  };
  return (
    <div className="space-y-2">
      {rows.map((r, i) => (
        <div key={i} className="flex gap-2">
          <input
            value={r}
            onChange={(e) => setAt(i, e.target.value)}
            inputMode="url"
            placeholder="粘贴对标视频 / 帖子链接"
            className="flex-1 text-sm bg-card border border-border rounded-[10px] px-3 py-2 focus:outline-none focus:border-accent focus:ring-2 focus:ring-ring/30"
          />
          <button
            type="button"
            onClick={() => removeAt(i)}
            className="shrink-0 size-9 rounded-[10px] border border-border text-muted-foreground active:scale-90 hover:text-primary"
            aria-label="删除链接"
          >
            ✕
          </button>
        </div>
      ))}
      <button
        type="button"
        onClick={() => onChange([...rows, ""])}
        className="text-xs font-medium text-accent active:opacity-60"
      >
        ＋ 再加一条链接
      </button>
    </div>
  );
}

function CountModal({
  onClose,
  onConfirm,
}: {
  onClose: () => void;
  onConfirm: (n: number) => void;
}) {
  const [n, setN] = useState(5);
  const presets = [1, 3, 5, 10, 20];
  const clamp = (v: number) => Math.max(1, Math.min(20, v));

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
        <h3 className="font-serif font-black text-xl text-center">这一批生成多少条？</h3>
        <p className="text-xs text-muted-foreground text-center mt-1">一次最多 20 条</p>

        <div className="flex justify-center gap-2 mt-5">
          {presets.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setN(p)}
              className={[
                "size-12 rounded-full font-bold text-sm transition-all active:scale-90",
                n === p
                  ? "bg-accent text-accent-foreground shadow-md shadow-accent/30"
                  : "bg-secondary text-secondary-foreground",
              ].join(" ")}
            >
              {p}
            </button>
          ))}
        </div>

        <div className="flex items-center justify-center gap-5 mt-5">
          <button
            type="button"
            onClick={() => setN((v) => clamp(v - 1))}
            className="size-10 rounded-full border-2 border-border text-xl font-bold active:scale-90 transition-transform"
          >
            −
          </button>
          <div className="text-center">
            <span className="font-serif font-black text-4xl tabular-nums">{n}</span>
            <span className="text-sm text-muted-foreground ml-1">条</span>
          </div>
          <button
            type="button"
            onClick={() => setN((v) => clamp(v + 1))}
            className="size-10 rounded-full border-2 border-border text-xl font-bold active:scale-90 transition-transform"
          >
            +
          </button>
        </div>

        <button
          type="button"
          onClick={() => onConfirm(clamp(n))}
          className="w-full mt-6 rounded-full py-3.5 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
        >
          开始生成 {n} 条
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

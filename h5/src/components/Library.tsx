import { useEffect, useMemo, useRef, useState } from "react";
import type { ScriptListItem, TaskDetail } from "../api/types";
import { useOptions } from "../hooks/useOptions";
import { colorOf } from "../lib/palette";
import ScriptReader from "./ScriptReader";

type Filter = { topicType?: string; scriptType?: string };

type Props = {
  items: ScriptListItem[];
  total: number;
  initialFilter: Filter;
  /** 正在跑的生成任务；为 null 时不显示进度条 */
  task: TaskDetail | null;
  onFilterChange: (f: Filter) => void;
  onDelete: (id: number) => void;
  onToast: (msg: string) => void;
};

// 接口返回 "2026-09-05 18:21:03"，列表展示到分钟
function fmt(ts: string): string {
  return ts ? ts.slice(0, 16) : "";
}

export default function Library({
  items,
  total,
  initialFilter,
  task,
  onFilterChange,
  onDelete,
  onToast,
}: Props) {
  const { topicTypes, scriptTypes } = useOptions();
  const [typeF, setTypeF] = useState(initialFilter.topicType ?? "全部");
  const [scriptF, setScriptF] = useState(initialFilter.scriptType ?? "全部");
  const [open, setOpen] = useState<ScriptListItem | null>(null);

  const topicTypeFilters = ["全部", ...topicTypes.map((t) => t.key)];

  // 筛选交给后端，选中后立即回传给 App 重新拉列表
  const applyFilter = (next: Filter) => {
    const f = {
      topicType: next.topicType ?? typeF,
      scriptType: next.scriptType ?? scriptF,
    };
    setTypeF(f.topicType);
    setScriptF(f.scriptType);
    onFilterChange(f);
  };

  const list = useMemo(
    () => [...items].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1)),
    [items],
  );

  const genCount = items.filter((s) => s.generated).length;

  return (
    <div className="pb-8">
      <div className="px-4 pt-5 pb-3">
        <p className="font-mono text-[11px] tracking-widest text-primary">SCRIPT LIBRARY · 文案库</p>
        <h2 className="font-serif font-black text-2xl leading-tight mt-1.5">
          共 {total} 条 · 选题 / 标题 / 脚本
        </h2>
        <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
          {genCount > 0 ? `其中 ${genCount} 条为本机生成` : "还没有生成记录，去「工作台」出一批"}
          <span className="text-muted-foreground/70"> · 左滑卡片可删除</span>
        </p>
      </div>

      {task && <GenerateProgress task={task} />}

      {/* Filters */}
      <div className="px-4 space-y-2 sticky top-0 z-10 bg-background/95 backdrop-blur py-2.5">
        <div className="flex gap-2 overflow-x-auto no-scrollbar">
          {topicTypeFilters.map((f) => (
            <FilterChip
              key={f}
              active={typeF === f}
              onClick={() => applyFilter({ topicType: f })}
              color={colorOf(f)}
            >
              {f}
            </FilterChip>
          ))}
        </div>
        <div className="flex gap-2 overflow-x-auto no-scrollbar">
          <FilterChip
            active={scriptF === "全部"}
            onClick={() => applyFilter({ scriptType: "全部" })}
            color="#3f3ca8"
          >
            全部脚本
          </FilterChip>
          {scriptTypes.map((s) => (
            <FilterChip
              key={s.key}
              active={scriptF === s.key}
              onClick={() => applyFilter({ scriptType: s.key })}
              color={colorOf(s.key)}
            >
              {s.key}
            </FilterChip>
          ))}
        </div>
      </div>

      {/* Cards */}
      <div className="px-4 pt-2 space-y-3">
        {list.map((s) => (
          <SwipeRow key={s.id} onDelete={() => onDelete(s.id)}>
            <button
              type="button"
              onClick={() => setOpen(s)}
              className="w-full text-left bg-card p-4 rounded-[16px] border border-border active:border-accent transition-colors"
            >
              <div className="flex items-center gap-2 mb-2">
                <span
                  className="font-mono text-[10px] px-2 py-0.5 rounded-full font-bold"
                  style={{ background: colorOf(s.scriptType) + "1a", color: colorOf(s.scriptType) }}
                >
                  {s.scriptType}
                </span>
                {s.generated && (
                  <span className="font-mono text-[10px] px-2 py-0.5 rounded-full bg-highlight/25 text-highlight-foreground">
                    生成
                  </span>
                )}
                <span className="font-mono text-[10px] text-muted-foreground ml-auto">
                  {s.words} 字
                </span>
              </div>
              <h3 className="font-serif font-bold text-[15px] leading-snug">{s.title}</h3>
              <p className="text-xs text-muted-foreground mt-1.5 leading-relaxed line-clamp-2">
                选题 · {s.topic}
              </p>
              <div className="mt-3 flex items-center justify-between">
                <div className="flex flex-wrap gap-1.5">
                  <Tag color={colorOf(s.topicType)}>{s.topicType}</Tag>
                  <Tag>{s.element}</Tag>
                </div>
                <span className="font-mono text-[10px] text-muted-foreground shrink-0">
                  🕘 {fmt(s.createdAt)}
                </span>
              </div>
            </button>
          </SwipeRow>
        ))}
        {list.length === 0 && (
          <p className="text-center text-sm text-muted-foreground py-16">
            该组合下暂无文案，去「工作台」生成一批吧
          </p>
        )}
      </div>

      {open && (
        <ScriptReader item={open} onClose={() => setOpen(null)} fmt={fmt} onToast={onToast} />
      )}
    </div>
  );
}

/**
 * 生成进度条。
 *
 * 一条文案要三次模型调用（选题标题 → 正文 → 去 AI 味），单条几十秒、
 * 一批 20 条要好几分钟，中间这段空白必须有交代，否则用户会以为没生成。
 *
 * 只看 doneCount 画进度是不够的：批内是并发跑的，小批量下几条会同时完成，
 * 进度会全程停在 0% 然后直接消失，看着像卡死。所以这里用逐条状态，
 * 并在还没有任何条目落地时把进度条画成"不确定"态——宁可不给数字，
 * 也不编一个假的百分比。
 */
function GenerateProgress({ task }: { task: TaskDetail }) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    const startedAt = Date.now();
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - startedAt) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [task.taskNo]);

  const running = task.items.filter((i) => i.status === "RUNNING").length;
  const settled = task.doneCount + task.failCount;
  const percent = task.total > 0 ? Math.round((settled / task.total) * 100) : 0;
  const measurable = settled > 0;

  return (
    <div className="px-4 pb-1">
      <div className="rounded-[16px] border border-accent/30 bg-accent/5 px-4 py-3.5">
        <div className="flex items-center gap-2.5">
          <span className="relative flex size-2.5 shrink-0">
            <span className="absolute inset-0 rounded-full bg-accent/40 animate-ping" />
            <span className="relative size-2.5 rounded-full bg-accent" />
          </span>
          <span className="font-serif font-bold text-[15px] text-foreground">
            后台正在生成，请稍后
          </span>
          <span className="ml-auto font-mono text-[11px] text-accent tabular-nums">
            {settled} / {task.total}
          </span>
        </div>

        <div className="mt-3 h-1.5 rounded-full bg-accent/15 overflow-hidden">
          {measurable ? (
            <div
              className="h-full rounded-full bg-accent transition-[width] duration-500 ease-out"
              style={{ width: `${percent}%` }}
            />
          ) : (
            // 还没有条目完成，进度不可测。整条低透明度脉动表示"在跑但说不准还要多久"
            <div className="h-full w-full rounded-full bg-accent/40 animate-pulse" />
          )}
        </div>

        <p className="text-[11px] text-muted-foreground mt-2 leading-relaxed tabular-nums">
          {running > 0 && `${running} 条正在写`}
          {running > 0 && settled > 0 && " · "}
          {settled > 0 && `已完成 ${task.doneCount} 条`}
          {(running > 0 || settled > 0) && " · "}
          已用 {elapsed} 秒
          {task.failCount > 0 && (
            <span className="text-primary"> · {task.failCount} 条未过自检，额度已退回</span>
          )}
        </p>
        <p className="text-[11px] text-muted-foreground/70 mt-1 leading-relaxed">
          每条要先出选题标题、再写正文、最后单独去一遍 AI 味，稍慢是正常的。
        </p>
      </div>
    </div>
  );
}

/* 左滑露出右侧「删除」按钮 */
function SwipeRow({ children, onDelete }: { children: React.ReactNode; onDelete: () => void }) {
  const [dx, setDx] = useState(0); // 0 ~ -MAX，负值向左
  const [open, setOpen] = useState(false);
  const startX = useRef(0);
  const dragging = useRef(false);
  const MAX = 84;

  const onDown = (e: React.PointerEvent) => {
    dragging.current = true;
    startX.current = e.clientX - (open ? -MAX : 0);
  };
  const onMove = (e: React.PointerEvent) => {
    if (!dragging.current) return;
    const d = e.clientX - startX.current;
    setDx(Math.max(-MAX, Math.min(0, d)));
  };
  const onUp = () => {
    dragging.current = false;
    const snap = dx < -MAX / 2;
    setOpen(snap);
    setDx(snap ? -MAX : 0);
  };

  return (
    <div className="relative overflow-hidden rounded-[16px]">
      {/* 删除按钮（右侧） */}
      <button
        type="button"
        onClick={onDelete}
        className="absolute inset-y-0 right-0 flex items-center justify-center bg-primary text-primary-foreground font-bold text-sm"
        style={{ width: MAX }}
      >
        删除
      </button>
      <div
        className="relative touch-pan-y"
        style={{ transform: `translateX(${dx}px)`, transition: dragging.current ? "none" : "transform .22s cubic-bezier(.2,.8,.2,1)" }}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerCancel={onUp}
      >
        {children}
      </div>
    </div>
  );
}

function FilterChip({
  active,
  onClick,
  color,
  children,
}: {
  active: boolean;
  onClick: () => void;
  color: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="shrink-0 px-3.5 py-1.5 text-xs rounded-full border font-medium transition-all active:scale-95"
      style={
        active
          ? { background: color, color: "#fff", borderColor: "transparent" }
          : { background: "transparent", color: "var(--muted-foreground)", borderColor: "var(--border)" }
      }
    >
      {children}
    </button>
  );
}

function Tag({ children, color }: { children: React.ReactNode; color?: string }) {
  return (
    <span
      className="font-mono text-[10px] px-2 py-0.5 rounded-full"
      style={
        color
          ? { background: color + "1a", color }
          : { background: "var(--muted)", color: "var(--muted-foreground)" }
      }
    >
      {children}
    </span>
  );
}

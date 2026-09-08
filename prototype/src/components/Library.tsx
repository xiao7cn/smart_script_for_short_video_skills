import { useMemo, useRef, useState } from "react";
import type { Script } from "../data/scripts";
import { scriptTypes } from "../data/config";
import { colorOf } from "../lib/palette";
import ScriptReader from "./ScriptReader";

type Props = {
  items: Script[];
  initialFilter: { topicType?: string; scriptType?: string };
  onDelete: (id: string) => void;
};

const topicTypeFilters = ["全部", "转化类", "破圈类", "家长类"];

function fmt(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export default function Library({ items, initialFilter, onDelete }: Props) {
  const [typeF, setTypeF] = useState(initialFilter.topicType ?? "全部");
  const [scriptF, setScriptF] = useState(initialFilter.scriptType ?? "全部");
  const [open, setOpen] = useState<Script | null>(null);

  const list = useMemo(
    () =>
      items
        .filter(
          (s) =>
            (typeF === "全部" || s.topicType === typeF) &&
            (scriptF === "全部" || s.scriptType === scriptF),
        )
        .sort((a, b) => b.createdAt - a.createdAt),
    [items, typeF, scriptF],
  );

  const genCount = items.filter((s) => s.generated).length;

  return (
    <div className="pb-8">
      <div className="px-4 pt-5 pb-3">
        <p className="font-mono text-[11px] tracking-widest text-primary">SCRIPT LIBRARY · 文案库</p>
        <h2 className="font-serif font-black text-2xl leading-tight mt-1.5">
          共 {items.length} 条 · 选题 / 标题 / 脚本
        </h2>
        <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
          {genCount > 0 ? `其中 ${genCount} 条为本机生成` : "已内置 15 条精选样稿"}
          <span className="text-muted-foreground/70"> · 左滑卡片可删除</span>
        </p>
      </div>

      {/* Filters */}
      <div className="px-4 space-y-2 sticky top-0 z-10 bg-background/95 backdrop-blur py-2.5">
        <div className="flex gap-2 overflow-x-auto no-scrollbar">
          {topicTypeFilters.map((f) => (
            <FilterChip key={f} active={typeF === f} onClick={() => setTypeF(f)} color={colorOf(f)}>
              {f}
            </FilterChip>
          ))}
        </div>
        <div className="flex gap-2 overflow-x-auto no-scrollbar">
          <FilterChip active={scriptF === "全部"} onClick={() => setScriptF("全部")} color="#3f3ca8">
            全部脚本
          </FilterChip>
          {scriptTypes.map((s) => (
            <FilterChip
              key={s.key}
              active={scriptF === s.key}
              onClick={() => setScriptF(s.key)}
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

      {open && <ScriptReader script={open} onClose={() => setOpen(null)} fmt={fmt} />}
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

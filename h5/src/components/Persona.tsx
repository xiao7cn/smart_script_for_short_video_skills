import { useEffect, useState } from "react";
import { persona as defaultPersona, type PersonaData } from "../data/config";

type Props = {
  persona: PersonaData;
  onChange: (p: PersonaData) => void;
  onReset: () => void;
  onBack?: () => void;
};

export default function Persona({ persona = defaultPersona, onChange, onReset, onBack }: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<PersonaData>(persona);

  // 外部人设变化（如恢复默认）时，非编辑态同步展示
  useEffect(() => {
    if (!editing) setDraft(persona);
  }, [persona, editing]);

  const set = <K extends keyof PersonaData>(k: K, v: PersonaData[K]) =>
    setDraft((d) => ({ ...d, [k]: v }));

  const startEdit = () => {
    setDraft(persona);
    setEditing(true);
  };
  const cancel = () => {
    setDraft(persona);
    setEditing(false);
  };
  const save = () => {
    onChange(draft);
    setEditing(false);
  };

  return (
    <div className="pb-32">
      {onBack && (
        <header className="sticky top-0 z-10 flex items-center gap-3 px-4 h-12 border-b border-border bg-background/95 backdrop-blur">
          <button
            type="button"
            onClick={onBack}
            className="text-sm text-accent font-medium active:opacity-60"
          >
            ← 我的
          </button>
          <span className="font-mono text-xs text-muted-foreground ml-auto">PERSONA</span>
        </header>
      )}
      <div className="px-4 pt-5 pb-4">
        <p className="font-mono text-[11px] tracking-widest text-primary">PERSONA · 固定人设</p>
        <div className="flex items-start gap-2">
          <h2 className="font-serif font-black text-2xl leading-tight mt-1.5 flex-1">
            {(editing ? draft.model : persona.model) || "未命名人设"} · 人设档案
          </h2>
          {!editing && (
            <button
              type="button"
              onClick={startEdit}
              className="mt-2 shrink-0 text-sm font-bold text-primary-foreground bg-primary rounded-full px-4 py-1.5 active:scale-95 transition-transform"
            >
              修改
            </button>
          )}
        </div>
        <p className="text-sm text-muted-foreground mt-2 leading-relaxed">
          {editing
            ? "编辑完成后点击底部「保存」，修改才会生效。"
            : "这些是每次生成都会注入的固定设定。点右上角「修改」进入编辑。"}
        </p>
      </div>

      <div className="px-4 space-y-3">
        {editing ? (
          <>
            <Line label="人设模型" value={draft.model} onChange={(v) => set("model", v)} />
            <Area label="模型说明" value={draft.modelDesc} onChange={(v) => set("modelDesc", v)} />
            <Area label="身份定位" value={draft.identity} onChange={(v) => set("identity", v)} />
            <Area label="价值定位" value={draft.value} onChange={(v) => set("value", v)} />
            <Line label="表达风格" value={draft.tone} onChange={(v) => set("tone", v)} />
            <Area label="目标人群" value={draft.audience} onChange={(v) => set("audience", v)} />

            <ListEditor
              title="用户需求"
              items={draft.needs}
              onChange={(needs) => set("needs", needs)}
              placeholder="补充一条用户痛点…"
            />

            <div className="border border-primary/40 bg-primary/5 rounded-[16px] p-4">
              <span className="font-mono text-xs text-primary font-bold">合规红线 · 通篇禁用词</span>
              <TagEditor
                items={draft.banned}
                onChange={(banned) => set("banned", banned)}
                placeholder="加禁用词"
              />
            </div>

            <button
              type="button"
              onClick={onReset}
              className="w-full text-xs font-medium text-muted-foreground border border-dashed border-border rounded-full py-2.5 active:scale-95"
            >
              恢复为默认人设
            </button>
          </>
        ) : (
          <>
            <ViewField label="人设模型" value={persona.model} desc={persona.modelDesc} />
            <ViewField label="身份定位" value={persona.identity} />
            <ViewField label="价值定位" value={persona.value} />
            <ViewField label="表达风格" value={persona.tone} />
            <ViewField label="目标人群" value={persona.audience} />

            <div className="border border-border bg-card rounded-[16px] overflow-hidden">
              <div className="px-4 py-2.5 border-b border-border">
                <span className="font-mono text-xs text-primary font-bold">用户需求</span>
              </div>
              <ul className="p-4 space-y-2">
                {persona.needs.map((n, i) => (
                  <li key={i} className="flex gap-2 text-sm text-foreground/85 leading-relaxed">
                    <span className="font-mono text-[11px] text-primary shrink-0 pt-0.5">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <span>{n}</span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="border border-primary/40 bg-primary/5 rounded-[16px] p-4">
              <span className="font-mono text-xs text-primary font-bold">合规红线 · 通篇禁用词</span>
              <div className="flex flex-wrap gap-2 mt-2.5">
                {persona.banned.map((b) => (
                  <span
                    key={b}
                    className="font-mono text-xs px-2.5 py-1 border border-primary/40 text-primary rounded-full line-through decoration-1"
                  >
                    {b}
                  </span>
                ))}
              </div>
              <p className="text-xs text-muted-foreground mt-3 leading-relaxed">
                拒绝贩卖焦虑与夸大吹嘘，用数据与事实说话。真诚是最高级的技巧。
              </p>
            </div>
          </>
        )}
      </div>

      {/* 编辑态底部保存栏 */}
      {editing && (
        <div className="fixed inset-x-0 bottom-0 z-30 flex justify-center pointer-events-none">
          <div className="w-full max-w-[460px] px-4 py-3 bg-background/95 backdrop-blur border-t border-border flex gap-2.5 pointer-events-auto">
            <button
              type="button"
              onClick={cancel}
              className="flex-1 rounded-full py-3 font-bold text-sm border-2 border-dashed border-border text-muted-foreground active:scale-95 transition-transform"
            >
              取消
            </button>
            <button
              type="button"
              onClick={save}
              className="flex-[2] rounded-full py-3 font-bold text-sm bg-primary text-primary-foreground shadow-lg shadow-primary/30 active:scale-95 transition-transform"
            >
              保存
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function ViewField({ label, value, desc }: { label: string; value: string; desc?: string }) {
  return (
    <div className="border border-border bg-card rounded-[16px] p-4">
      <span className="font-mono text-xs text-primary font-bold">{label}</span>
      <p className="font-serif text-[15px] leading-relaxed mt-1.5 text-foreground">{value}</p>
      {desc && <p className="text-xs text-muted-foreground mt-1.5 leading-relaxed">{desc}</p>}
    </div>
  );
}

function Line({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block border border-border bg-card rounded-[16px] p-4 focus-within:border-accent transition-colors">
      <span className="font-mono text-xs text-primary font-bold">{label}</span>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-2 w-full bg-transparent font-serif text-[15px] text-foreground outline-none placeholder:text-muted-foreground/50"
      />
    </label>
  );
}

function Area({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block border border-border bg-card rounded-[16px] p-4 focus-within:border-accent transition-colors">
      <span className="font-mono text-xs text-primary font-bold">{label}</span>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        rows={2}
        className="mt-2 w-full bg-transparent font-serif text-[15px] leading-relaxed text-foreground outline-none resize-none placeholder:text-muted-foreground/50 field-sizing-content"
      />
    </label>
  );
}

function ListEditor({
  title,
  items,
  onChange,
  placeholder,
}: {
  title: string;
  items: string[];
  onChange: (v: string[]) => void;
  placeholder: string;
}) {
  const [draft, setDraft] = useState("");
  const add = () => {
    const v = draft.trim();
    if (!v) return;
    onChange([...items, v]);
    setDraft("");
  };
  return (
    <div className="border border-border bg-card rounded-[16px] overflow-hidden">
      <div className="px-4 py-2.5 border-b border-border">
        <span className="font-mono text-xs text-accent font-bold">{title}</span>
      </div>
      <ul className="p-3 space-y-2">
        {items.map((n, i) => (
          <li key={i} className="flex gap-2 items-start">
            <span className="font-mono text-[11px] text-accent shrink-0 pt-2">
              {String(i + 1).padStart(2, "0")}
            </span>
            <input
              value={n}
              onChange={(e) => onChange(items.map((x, j) => (j === i ? e.target.value : x)))}
              className="flex-1 bg-secondary/60 rounded-[10px] px-2.5 py-1.5 text-sm text-foreground/90 outline-none focus:bg-secondary"
            />
            <button
              type="button"
              onClick={() => onChange(items.filter((_, j) => j !== i))}
              className="shrink-0 size-8 rounded-[10px] text-muted-foreground active:scale-90 hover:text-primary"
              aria-label="删除"
            >
              ✕
            </button>
          </li>
        ))}
        <li className="flex gap-2">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && add()}
            placeholder={placeholder}
            className="flex-1 bg-transparent border border-dashed border-border rounded-[10px] px-2.5 py-1.5 text-sm outline-none focus:border-accent placeholder:text-muted-foreground/50"
          />
          <button
            type="button"
            onClick={add}
            className="shrink-0 px-3 rounded-[10px] bg-accent text-accent-foreground text-sm font-bold active:scale-95"
          >
            添加
          </button>
        </li>
      </ul>
    </div>
  );
}

function TagEditor({
  items,
  onChange,
  placeholder,
}: {
  items: string[];
  onChange: (v: string[]) => void;
  placeholder: string;
}) {
  const [draft, setDraft] = useState("");
  const add = () => {
    const v = draft.trim();
    if (!v || items.includes(v)) return;
    onChange([...items, v]);
    setDraft("");
  };
  return (
    <div className="flex flex-wrap gap-2 mt-2.5 items-center">
      {items.map((b) => (
        <span
          key={b}
          className="font-mono text-xs pl-2.5 pr-1.5 py-1 border border-primary/40 text-primary rounded-full flex items-center gap-1"
        >
          <span className="line-through decoration-1">{b}</span>
          <button
            type="button"
            onClick={() => onChange(items.filter((x) => x !== b))}
            className="text-primary/60 active:scale-90"
            aria-label="移除"
          >
            ✕
          </button>
        </span>
      ))}
      <input
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && add()}
        placeholder={placeholder}
        className="min-w-[80px] flex-1 bg-transparent text-xs outline-none placeholder:text-primary/50 py-1"
      />
    </div>
  );
}

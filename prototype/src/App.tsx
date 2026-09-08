import { useEffect, useMemo, useState } from "react";
import Auth, { type User } from "./components/Auth";
import Logo from "./components/Logo";
import Builder from "./components/Builder";
import Library from "./components/Library";
import Profile from "./components/Profile";
import { persona as defaultPersona, type PersonaData } from "./data/config";
import { seedScripts, type Script } from "./data/scripts";
import { generateBatch, type WizardSel } from "./lib/generate";

type Tab = "builder" | "library" | "profile";

const tabs: { key: Tab; label: string; en: string }[] = [
  { key: "builder", label: "工作台", en: "MAKE" },
  { key: "library", label: "文案库", en: "SCRIPTS" },
  { key: "profile", label: "我的", en: "ME" },
];

const LS_ITEMS = "kbwf.items.v1";
const LS_PERSONA = "kbwf.persona.v1";
const LS_USER = "kbwf.user.v1";
const LS_CREDITS = "kbwf.credits.v1";
const FREE_CREDITS = 20; // 新用户赠送额度

function loadCredits(): number {
  try {
    const raw = localStorage.getItem(LS_CREDITS);
    if (raw !== null) return Number(raw) || 0;
  } catch {
    /* ignore */
  }
  return FREE_CREDITS;
}

function loadUser(): User | null {
  try {
    const raw = localStorage.getItem(LS_USER);
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

function loadItems(): Script[] {
  try {
    const raw = localStorage.getItem(LS_ITEMS);
    if (raw) return JSON.parse(raw) as Script[];
  } catch {
    /* ignore */
  }
  return seedScripts;
}

function loadPersona(): PersonaData {
  try {
    const raw = localStorage.getItem(LS_PERSONA);
    if (raw) return { ...defaultPersona, ...(JSON.parse(raw) as PersonaData) };
  } catch {
    /* ignore */
  }
  return defaultPersona;
}

export default function App() {
  const [tab, setTab] = useState<Tab>("builder");
  const [libFilter, setLibFilter] = useState<{ topicType?: string; scriptType?: string }>({});
  const [items, setItems] = useState<Script[]>(loadItems);
  const [persona, setPersona] = useState<PersonaData>(loadPersona);
  const [toast, setToast] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(loadUser);
  const [credits, setCredits] = useState<number>(loadCredits);

  useEffect(() => {
    try {
      localStorage.setItem(LS_CREDITS, String(credits));
    } catch {
      /* ignore */
    }
  }, [credits]);

  useEffect(() => {
    try {
      localStorage.setItem(LS_ITEMS, JSON.stringify(items));
    } catch {
      /* ignore */
    }
  }, [items]);

  useEffect(() => {
    try {
      localStorage.setItem(LS_PERSONA, JSON.stringify(persona));
    } catch {
      /* ignore */
    }
  }, [persona]);

  const nextN = useMemo(() => items.reduce((m, s) => Math.max(m, s.n), 0) + 1, [items]);
  const stats = useMemo(
    () => ({ total: items.length, generated: items.filter((s) => s.generated).length }),
    [items],
  );

  const login = (u: User) => {
    try {
      localStorage.setItem(LS_USER, JSON.stringify(u));
    } catch {
      /* ignore */
    }
    setUser(u);
    setTab("builder");
  };

  const logout = () => {
    try {
      localStorage.removeItem(LS_USER);
    } catch {
      /* ignore */
    }
    setUser(null);
  };

  const handleGenerate = (sel: WizardSel, count: number) => {
    if (credits <= 0) {
      flash("额度不足，请先充值");
      setTab("profile");
      return;
    }
    const n = Math.min(count, credits);
    const batch = generateBatch(sel, n, persona, nextN);
    setItems((prev) => [...batch, ...prev]);
    setCredits((c) => c - n);
    setLibFilter({});
    setTab("library");
    flash(n < count ? `额度仅够生成 ${n} 条，已生成` : `已生成 ${n} 条并存入文案库`);
  };

  const recharge = (add: number, label: string) => {
    setCredits((c) => c + add);
    flash(`充值成功，到账 ${label}`);
  };

  const deleteItem = (id: string) => setItems((prev) => prev.filter((s) => s.id !== id));

  const resetPersona = () => {
    setPersona(defaultPersona);
    flash("已恢复默认人设");
  };

  const flash = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 2000);
  };

  if (!user) return <Auth onAuth={login} />;

  return (
    <div className="h-full w-full bg-paper text-foreground flex justify-center overflow-hidden">
      <div className="relative w-full max-w-[460px] h-full bg-background border-x border-border flex flex-col overflow-hidden">
        {/* Header */}
        <header className="shrink-0 h-14 flex items-center px-4 gap-2.5 bg-card border-b border-border">
          <div className="size-8 bg-primary text-primary-foreground flex items-center justify-center rounded-[10px]">
            <Logo className="size-5" />
          </div>
          <div className="leading-none">
            <h1 className="font-serif font-black text-[15px] text-foreground">闪创工厂</h1>
            <p className="font-mono text-[9px] text-muted-foreground tracking-widest mt-0.5">
              AI SHORT-VIDEO SCRIPT STUDIO
            </p>
          </div>
          <span className="ml-auto font-mono text-[10px] text-muted-foreground border border-border px-2 py-0.5 rounded-full">
            H5
          </span>
        </header>

        {/* Body */}
        <main className="flex-1 overflow-auto relative">
          {tab === "builder" && <Builder persona={persona} onGenerate={handleGenerate} />}
          {tab === "library" && (
            <Library
              key={JSON.stringify(libFilter)}
              items={items}
              initialFilter={libFilter}
              onDelete={deleteItem}
            />
          )}
          {tab === "profile" && (
            <Profile
              user={user}
              stats={stats}
              credits={credits}
              persona={persona}
              onPersonaChange={setPersona}
              onPersonaReset={resetPersona}
              onRecharge={recharge}
              onLogout={logout}
            />
          )}
        </main>

        {/* Toast */}
        {toast && (
          <div className="absolute left-1/2 -translate-x-1/2 bottom-24 z-40 animate-[pop_.2s_ease]">
            <div className="bg-foreground text-background text-sm font-medium px-4 py-2.5 rounded-full shadow-xl">
              {toast}
            </div>
          </div>
        )}

        {/* Bottom tab bar */}
        <nav className="shrink-0 border-t border-border bg-background grid grid-cols-3">
          {tabs.map((t) => {
            const active = tab === t.key;
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => setTab(t.key)}
                className="flex flex-col items-center gap-0.5 py-2.5 transition-colors relative"
              >
                {active && (
                  <span className="absolute top-0 inset-x-6 h-0.5 bg-primary rounded-full" />
                )}
                <span
                  className={[
                    "font-serif font-bold text-sm transition-colors",
                    active ? "text-primary" : "text-muted-foreground",
                  ].join(" ")}
                >
                  {t.label}
                </span>
                <span
                  className={[
                    "font-mono text-[8px] tracking-widest transition-colors",
                    active ? "text-primary/70" : "text-muted-foreground/60",
                  ].join(" ")}
                >
                  {t.en}
                </span>
              </button>
            );
          })}
        </nav>
      </div>
    </div>
  );
}

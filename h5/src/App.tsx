import { useEffect, useRef, useState } from "react";
import Auth from "./components/Auth";
import Logo from "./components/Logo";
import Builder from "./components/Builder";
import Library from "./components/Library";
import Profile from "./components/Profile";
import { persona as defaultPersona } from "./data/config";
import { logout as apiLogout, me } from "./api/auth";
import { errCode, errMsg } from "./api/client";
import { recharge as apiRecharge } from "./api/credit";
import {
  getPersona,
  resetPersona as apiResetPersona,
  savePersona as apiSavePersona,
} from "./api/persona";
import { deleteScript, generate, listScripts } from "./api/script";
import type {
  ApiUser,
  CreditPack,
  LoginResult,
  PersonaData,
  ScriptListItem,
  UserStats,
  WizardSel,
} from "./api/types";
import { useTaskPolling } from "./hooks/useTaskPolling";
import { session } from "./store/session";
import { useToast } from "./store/toast";

type Tab = "builder" | "library" | "profile";
type LibFilter = { topicType?: string; scriptType?: string };

const tabs: { key: Tab; label: string; en: string }[] = [
  { key: "builder", label: "工作台", en: "MAKE" },
  { key: "library", label: "文案库", en: "SCRIPTS" },
  { key: "profile", label: "我的", en: "ME" },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("builder");
  const [libFilter, setLibFilter] = useState<LibFilter>({});
  const [items, setItems] = useState<ScriptListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [persona, setPersona] = useState<PersonaData>(defaultPersona);
  const [user, setUser] = useState<ApiUser | null>(session.user());
  const [credits, setCredits] = useState(0);
  const [stats, setStats] = useState<UserStats>({ total: 0, generated: 0 });
  const [taskNo, setTaskNo] = useState<string | null>(null);
  const { toast, flash } = useToast();
  // 轮询刷新时要沿用当前筛选条件
  const filterRef = useRef<LibFilter>({});

  const loadScripts = async (f: LibFilter = filterRef.current, silent = false) => {
    filterRef.current = f;
    try {
      const page = await listScripts({
        topicType: f.topicType === "全部" ? undefined : f.topicType,
        scriptType: f.scriptType === "全部" ? undefined : f.scriptType,
      });
      setItems(page.records);
      setTotal(page.total);
    } catch (e) {
      if (!silent) flash(errMsg(e));
    }
  };

  // 额度与统计一律以服务端为准
  const syncAccount = async () => {
    const info = await me();
    session.setUser(info.user);
    setUser(info.user);
    setCredits(info.credits);
    setStats(info.stats);
  };

  const bootstrap = async () => {
    try {
      await syncAccount();
    } catch (e) {
      if (errCode(e) !== 401) flash(errMsg(e));
      session.clear(); // 会话恢复失败就退回登录
      return;
    }
    void getPersona()
      .then(setPersona)
      .catch(() => {
        /* 用兜底人设 */
      });
    void loadScripts({});
  };

  useEffect(() => {
    // 令牌失效时 api 层会清会话，这里跟着退回登录页
    const off = session.subscribe((s) => {
      if (s) return;
      setUser(null);
      setItems([]);
      setTotal(0);
      setCredits(0);
      setStats({ total: 0, generated: 0 });
      setPersona(defaultPersona);
      setTaskNo(null);
      setTab("builder");
    });
    if (session.token()) void bootstrap();
    return off;
  }, []);

  // 返回的 task 要交给文案库画进度条，不能丢
  const task = useTaskPolling(taskNo, {
    onProgress: () => void loadScripts(filterRef.current, true),
    onSettled: () => {
      setTaskNo(null);
      void loadScripts(filterRef.current, true);
      void syncAccount().catch(() => {
        /* 忽略 */
      });
    },
    onTimeout: () => {
      setTaskNo(null);
      flash("仍在生成，稍后刷新查看");
    },
  });

  const login = (r: LoginResult) => {
    session.set({ token: r.token, user: r.user });
    setUser(r.user);
    setCredits(r.credits);
    setTab("builder");
    void bootstrap();
  };

  const logout = () => {
    void apiLogout().catch(() => {
      /* 本地会话照样清掉 */
    });
    session.clear();
  };

  const handleGenerate = async (sel: WizardSel, count: number, promptOverride?: string) => {
    try {
      const res = await generate(sel, count, promptOverride);
      setCredits(res.creditsBalance);
      setLibFilter({});
      setTab("library");
      setTaskNo(res.taskNo); // 交给 useTaskPolling 跟进度
      void loadScripts({}, true);
      // 生成是异步的，提交这一刻还没出稿。进度条已经把过程讲清楚了，
      // 这里就别再说「已生成」——两个说法对不上，用户会以为出问题了
      flash(
        res.message ??
          (res.accepted < count
            ? `额度仅够 ${res.accepted} 条，已开始生成`
            : `已提交 ${res.accepted} 条，正在生成`),
      );
    } catch (e) {
      if (errCode(e) === 3001) {
        flash("额度不足，请先充值");
        setTab("profile");
        return;
      }
      flash(errMsg(e));
    }
  };

  const recharge = async (pack: CreditPack) => {
    try {
      const r = await apiRecharge(pack.packId);
      setCredits(r.balance);
      flash(`充值成功，到账 ${pack.base} 条${pack.bonus ? ` + 赠 ${pack.bonus} 条` : ""}`);
    } catch (e) {
      flash(errMsg(e));
    }
  };

  const deleteItem = async (id: number) => {
    try {
      await deleteScript(id);
      setItems((prev) => prev.filter((s) => s.id !== id));
      setTotal((t) => Math.max(0, t - 1));
      void syncAccount().catch(() => {
        /* 忽略 */
      });
    } catch (e) {
      flash(errMsg(e));
    }
  };

  const changePersona = async (p: PersonaData) => {
    setPersona(p); // 先本地生效，保住原型的即时反馈
    try {
      setPersona(await apiSavePersona(p));
    } catch (e) {
      flash(errMsg(e));
    }
  };

  const resetPersona = async () => {
    try {
      setPersona(await apiResetPersona());
      flash("已恢复默认人设");
    } catch (e) {
      flash(errMsg(e));
    }
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
              total={total}
              initialFilter={libFilter}
              task={task}
              onFilterChange={(f) => void loadScripts(f)}
              onDelete={deleteItem}
              onToast={flash}
            />
          )}
          {tab === "profile" && (
            <Profile
              user={user}
              stats={stats}
              credits={credits}
              persona={persona}
              onPersonaChange={changePersona}
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

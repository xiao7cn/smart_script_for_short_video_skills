import type { ApiUser } from "../api/types";

const LS_SESSION = "kbwf.session.v1";

export type SessionData = { token: string; user: ApiUser };

type Listener = (s: SessionData | null) => void;

function load(): SessionData | null {
  try {
    const raw = localStorage.getItem(LS_SESSION);
    return raw ? (JSON.parse(raw) as SessionData) : null;
  } catch {
    return null;
  }
}

let current: SessionData | null = load();
const listeners = new Set<Listener>();

function persist() {
  try {
    if (current) localStorage.setItem(LS_SESSION, JSON.stringify(current));
    else localStorage.removeItem(LS_SESSION);
  } catch {
    /* ignore */
  }
}

function emit() {
  listeners.forEach((fn) => fn(current));
}

export const session = {
  get: () => current,
  token: () => current?.token ?? null,
  user: () => current?.user ?? null,
  set(data: SessionData) {
    current = data;
    persist();
    emit();
  },
  setUser(user: ApiUser) {
    if (!current) return;
    current = { ...current, user };
    persist();
    emit();
  },
  clear() {
    if (!current) return;
    current = null;
    persist();
    emit();
  },
  subscribe(fn: Listener): () => void {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
};

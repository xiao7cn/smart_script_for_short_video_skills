const KEY = "kbwf.session.v1";

function load() {
  try {
    const raw = wx.getStorageSync(KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

let current = load();
const listeners = [];

function persist() {
  try {
    if (current) wx.setStorageSync(KEY, JSON.stringify(current));
    else wx.removeStorageSync(KEY);
  } catch (e) {
    /* ignore */
  }
}

function emit() {
  listeners.forEach((fn) => fn(current));
}

const session = {
  get: () => current,
  token: () => (current && current.token) || null,
  user: () => (current && current.user) || null,
  set(data) {
    current = data;
    persist();
    emit();
  },
  setUser(user) {
    if (!current) return;
    current = { token: current.token, user };
    persist();
    emit();
  },
  clear() {
    if (!current) return;
    current = null;
    persist();
    emit();
  },
  subscribe(fn) {
    listeners.push(fn);
    return () => {
      const i = listeners.indexOf(fn);
      if (i >= 0) listeners.splice(i, 1);
    };
  },
};

module.exports = { session };

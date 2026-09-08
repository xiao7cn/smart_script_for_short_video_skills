// 字段与《接口设计》严格一致，前端不做重命名，避免两侧口径漂移。

/* ---------- 通用 ---------- */

export type Page<T> = {
  records: T[];
  total: number;
  page: number;
  size: number;
};

/* ---------- 账号 ---------- */

export type ApiUser = {
  userId: number;
  nickname: string;
  account: string;
  via: "wechat" | "phone";
  avatar?: string | null;
};

export type SmsCodeResult = {
  sent: boolean;
  cooldown: number;
  devCode?: string | null; // 仅非生产环境返回
};

export type LoginResult = {
  token: string;
  expiresIn: number;
  firstLogin: boolean;
  user: ApiUser;
  credits: number;
};

export type UserStats = { total: number; generated: number };

export type MeResult = {
  user: ApiUser;
  credits: number;
  stats: UserStats;
};

/* ---------- 向导选项 ---------- */

export type TopicTypeOption = { key: string; name: string; tag: string; desc: string };
export type TopicSourceOption = { key: string; desc: string; ready: boolean; note?: string };
export type ViralElementOption = { key: string; hint: string };
export type ScriptTypeOption = {
  key: string;
  ratio: number;
  goal: string;
  formula: string;
  desc: string;
};

export type Options = {
  topicTypes: TopicTypeOption[];
  topicSources: TopicSourceOption[];
  gridInner: string[];
  gridMiddle: string[];
  gridOuter: string[];
  viralElements: ViralElementOption[];
  scriptTypes: ScriptTypeOption[];
  refSources: string[]; // 触发「对标视频」步骤的来源
  deaiPrompt: string;
};

/* ---------- 人设档案 ---------- */

export type PersonaData = {
  model: string;
  modelDesc: string;
  identity: string;
  value: string;
  tone: string;
  audience: string;
  needs: string[];
  banned: string[];
  minWords: number;
  ctaStyle: string;
  ctaAsset: string;
  platform: string;
};

/* ---------- 向导参数与提示词 ---------- */

export type WizardSel = {
  topicType?: string[];
  source?: string[];
  inner?: string[];
  middle?: string[];
  outer?: string[];
  element?: string[];
  scriptType?: string[];
  topicDraft?: string;
  refs?: string[];
  autoSearch?: boolean;
};

export type PromptPreview = { prompt: string; deaiPrompt: string };

/* ---------- 生成任务 ---------- */

export type GenerateResult = {
  taskNo: string;
  accepted: number;
  requested: number;
  creditsHold: number;
  creditsBalance: number;
  message?: string | null;
};

export type TaskStatus = "PENDING" | "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED" | "CANCELLED";

export type TaskItem = { idx: number; status: TaskStatus; scriptId: number | null };

export type TaskDetail = {
  taskNo: string;
  type: string;
  status: TaskStatus;
  total: number;
  doneCount: number;
  failCount: number;
  items: TaskItem[];
  creditsSettled: number;
  errorCode?: string | null;
  errorMsg?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
};

/* ---------- 文案库 ---------- */

export type ScriptBreakdown = {
  refs?: string[];
  autoSearch?: boolean;
  original: string;
  points: { label: string; detail: string }[];
  rewrite: string;
};

// 列表不返回 body / structure / breakdown
export type ScriptListItem = {
  id: number;
  seqNo: number;
  title: string;
  topic: string;
  scriptType: string;
  topicType: string;
  source: string;
  grid: string;
  element: string;
  words: number;
  generated: boolean;
  hasBreakdown: boolean;
  createdAt: string; // "2026-09-05 18:21:03"
};

export type ScriptDetail = {
  id: number;
  seqNo: number;
  title: string;
  topic: string;
  scriptType: string;
  topicType: string;
  source: string;
  grid: string;
  element: string;
  structure: string;
  words: number;
  generated: boolean;
  needsMaterial?: string | null;
  body: string;
  createdAt: string;
  breakdown?: ScriptBreakdown | null;
};

export type ScriptQuery = {
  topicType?: string;
  scriptType?: string;
  page?: number;
  size?: number;
};

/* ---------- 爆款拆解 ---------- */

export type TeardownInput = {
  url?: string;
  browser?: string;
  fileId?: string;
  text?: string;
  videoTitle?: string;
};

export type TeardownSubmitResult = { taskNo: string; teardownId: number | null };

export type TeardownDetail = {
  id: number;
  platform: string;
  sourceUrl?: string | null;
  videoTitle: string;
  durationSec: number;
  words: number;
  speechRate: number;
  asrBackend: string;
  transcript: string;
  teardown: {
    hook: { type: string; seconds: number; text: string; why: string; weakness: string };
    midHooks: { seconds: number; text: string; device: string }[];
    ending: { type: string; natural: boolean; advice: string };
    skeleton: { seq: number; role: string; summary: string }[];
    grid: { inner: string; middle: string; outer: string };
    element: string;
  };
  framework: {
    steps: string[];
    insights: { angle: string; why: string }[];
  };
  status: "PENDING" | "NEED_FILE" | "SUCCESS" | "FAILED";
  fetchGuide?: string | null;
};

/* ---------- 文案重写 ---------- */

export type RewriteInput = {
  teardownId?: number;
  originalTitle?: string;
  originalBody?: string;
  purpose?: string;
  targetWords?: number;
  extraViews?: string;
  extraBanned?: string[];
};

export type RewriteSubmitResult = { taskNo: string; rewriteId: number | null };

export type RewriteDetail = {
  id: number;
  teardownId: number | null;
  originalTitle: string;
  originalBody: string;
  myTitle: string;
  sections: Record<string, string>;
  finalBody: string;
  words: number;
  scores: Record<string, number>;
  readaloudPass: boolean;
  overlapMax: number;
  checkReport: string;
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAILED";
};

export type RewritePublishResult = { scriptId: number };

/* ---------- 额度与充值 ---------- */

export type CreditOverview = {
  balance: number;
  hold: number;
  totalGranted: number;
  totalRecharged: number;
  totalConsumed: number;
};

export type CreditPack = {
  packId: string;
  priceFen: number;
  base: number;
  bonus: number;
};

export type RechargeResult = {
  orderNo: string;
  status: "PAID" | "PENDING" | "FAILED";
  credited: number;
  balance: number;
};

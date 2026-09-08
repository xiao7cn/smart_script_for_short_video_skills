import { api } from "./client";
import type { RewriteDetail, RewriteInput, RewritePublishResult, RewriteSubmitResult } from "./types";

// teardownId 与 originalBody 至少给一个
export const submitRewrite = (input: RewriteInput) =>
  api.post<RewriteSubmitResult>("/rewrites", input);

export const getRewrite = (id: number) => api.get<RewriteDetail>(`/rewrites/${id}`);

// 把第九段定稿写入文案库
export const publishRewrite = (id: number) =>
  api.post<RewritePublishResult>(`/rewrites/${id}/publish`);

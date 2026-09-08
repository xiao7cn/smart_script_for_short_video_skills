import { api } from "./client";
import type { TeardownDetail, TeardownInput, TeardownSubmitResult } from "./types";

// url / fileId / text 三选一
export const submitTeardown = (input: TeardownInput) =>
  api.post<TeardownSubmitResult>("/teardowns", input);

export const getTeardown = (id: number) => api.get<TeardownDetail>(`/teardowns/${id}`);

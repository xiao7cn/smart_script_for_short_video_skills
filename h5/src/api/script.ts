import { api, qs } from "./client";
import type {
  GenerateResult,
  Page,
  ScriptDetail,
  ScriptListItem,
  ScriptQuery,
  WizardSel,
} from "./types";

const DEFAULT_SIZE = 100;

export const generate = (sel: WizardSel, count: number, promptOverride?: string) =>
  api.post<GenerateResult>("/scripts/generate", {
    sel,
    count,
    promptOverride: promptOverride ?? null,
  });

export const listScripts = (query: ScriptQuery = {}) =>
  api.get<Page<ScriptListItem>>(
    "/scripts" +
      qs({
        topicType: query.topicType,
        scriptType: query.scriptType,
        page: query.page ?? 1,
        size: query.size ?? DEFAULT_SIZE,
      }),
  );

export const getScript = (id: number) => api.get<ScriptDetail>(`/scripts/${id}`);

export const deleteScript = (id: number) => api.del<null>(`/scripts/${id}`);

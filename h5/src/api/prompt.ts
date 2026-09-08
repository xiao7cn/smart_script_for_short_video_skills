import { api } from "./client";
import type { PromptPreview, WizardSel } from "./types";

export const previewPrompt = (sel: WizardSel) =>
  api.post<PromptPreview>("/prompts/preview", { sel });

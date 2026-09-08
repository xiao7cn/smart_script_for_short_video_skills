import { api } from "./client";
import type { CreditOverview, CreditPack, RechargeResult } from "./types";

export const getCredits = () => api.get<CreditOverview>("/credits");

export const getPacks = () => api.get<CreditPack[]>("/credits/packs");

export const recharge = (packId: string) =>
  api.post<RechargeResult>("/credits/recharge", { packId });

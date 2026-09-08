import { api } from "./client";
import type { PersonaData } from "./types";

export const getPersona = () => api.get<PersonaData>("/persona");

// 全量覆盖，未在编辑界面出现的字段也要原样带回
export const savePersona = (persona: PersonaData) => api.put<PersonaData>("/persona", persona);

export const resetPersona = () => api.post<PersonaData>("/persona/reset");

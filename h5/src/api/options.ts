import { api } from "./client";
import type { Options } from "./types";

export const getOptions = () => api.get<Options>("/options");

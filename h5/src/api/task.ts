import { api } from "./client";
import type { TaskDetail } from "./types";

export const getTask = (taskNo: string) => api.get<TaskDetail>(`/tasks/${taskNo}`);

import { useEffect, useRef, useState } from "react";
import { getTask } from "../api/task";
import type { TaskDetail, TaskStatus } from "../api/types";

const INTERVAL = 2000;
const TIMEOUT = 10 * 60 * 1000;
const SETTLED: TaskStatus[] = ["SUCCESS", "PARTIAL", "FAILED", "CANCELLED"];

type Handlers = {
  onProgress?: (task: TaskDetail) => void;
  onSettled?: (task: TaskDetail) => void;
  onTimeout?: () => void;
};

/** 2 秒轮询任务进度；页面隐藏时暂停，终态或超时停止 */
export function useTaskPolling(taskNo: string | null, handlers: Handlers): TaskDetail | null {
  const [task, setTask] = useState<TaskDetail | null>(null);
  const cbs = useRef(handlers);
  cbs.current = handlers;

  useEffect(() => {
    if (!taskNo) {
      setTask(null);
      return;
    }

    let stopped = false;
    let timer: ReturnType<typeof setInterval> | null = null;
    let settledCount = -1; // 上一轮已结束的条目数，只在变化时回调
    const startedAt = Date.now();

    const stop = () => {
      stopped = true;
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
    };

    const tick = async () => {
      if (stopped || document.hidden) return;
      if (Date.now() - startedAt > TIMEOUT) {
        stop();
        cbs.current.onTimeout?.();
        return;
      }
      try {
        const t = await getTask(taskNo);
        if (stopped) return;
        setTask(t);
        const done = t.doneCount + t.failCount;
        if (done !== settledCount) {
          settledCount = done;
          cbs.current.onProgress?.(t);
        }
        if (SETTLED.includes(t.status)) {
          stop();
          cbs.current.onSettled?.(t);
        }
      } catch {
        // 单次失败不打断轮询，下个周期再试
      }
    };

    // 从后台回到前台立即补一次，不等下一个周期
    const onVisible = () => {
      if (!document.hidden) void tick();
    };

    void tick();
    timer = setInterval(() => void tick(), INTERVAL);
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      stop();
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [taskNo]);

  return task;
}

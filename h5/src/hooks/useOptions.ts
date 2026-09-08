import { useEffect, useState } from "react";
import { getOptions } from "../api/options";
import { defaultOptions } from "../data/config";
import type { Options } from "../api/types";

// 选项全局只拉一次，多个页面共用同一份缓存
let cache: Options | null = null;
let inflight: Promise<Options> | null = null;

function load(): Promise<Options> {
  if (cache) return Promise.resolve(cache);
  if (!inflight) {
    inflight = getOptions()
      .then((o) => {
        cache = o;
        return o;
      })
      // 接口不可用时回落到内置默认值，向导始终能走完
      .catch(() => defaultOptions)
      .finally(() => {
        inflight = null;
      });
  }
  return inflight;
}

export function useOptions(): Options {
  const [options, setOptions] = useState<Options>(cache ?? defaultOptions);

  useEffect(() => {
    let alive = true;
    void load().then((o) => {
      if (alive) setOptions(o);
    });
    return () => {
      alive = false;
    };
  }, []);

  return options;
}

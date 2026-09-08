package com.shanchuang.harness;

import com.shanchuang.harness.HarnessContract.Health;
import com.shanchuang.harness.HarnessContract.RunRequest;
import com.shanchuang.harness.HarnessContract.RunResult;

/**
 * Harness SPI。
 *
 * 原有能力跑在 Cursor / Claude 这类自带 harness 的环境里（工具调用循环、会话状态、
 * 文件读写、上下文压缩）。Java 后端本身没有这些，所以把 harness 抽成这一层：
 * 业务只依赖这个接口，首版实现是 pi（Node sidecar），后续换 deepseek harness
 * 或自研只需新增一个实现类。
 *
 * 实现约定：run() 不抛异常，失败一律用 RunResult.ok=false + errorCode 表达，
 * 让上层能按错误码决定是重试、退额度还是打回重写。
 */
public interface HarnessClient {

    /** harness 名称：pi / mock / deepseek */
    String name();

    /** 跑一次到收敛 */
    RunResult run(RunRequest request);

    /** 健康检查，供管理端展示 provider 就绪情况 */
    Health health();
}

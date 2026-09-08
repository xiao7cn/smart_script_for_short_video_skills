package com.shanchuang.harness;

import java.util.Optional;

/**
 * 当前该用哪个 harness 的来源。
 *
 * 定义在 harness 包里、由 aiconfig 模块实现，是为了避免 harness 反向依赖业务模块：
 * 没有实现 bean 时（例如单测只装配 harness 层）自动回落到 application.yml 的配置。
 */
public interface HarnessSelectionSource {

    /** 库里 is_default=1 且 enabled=1 的 harness 名称 */
    Optional<String> defaultHarnessName();

    /** 该 harness 在库里配置的端点，用于覆盖配置文件里的默认值 */
    Optional<String> endpointOf(String name);
}

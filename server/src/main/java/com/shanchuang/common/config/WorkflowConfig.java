package com.shanchuang.common.config;

import com.shanchuang.workflow.PromptBuilder;
import com.shanchuang.workflow.QualityGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * workflow 层的 bean。
 *
 * 这两个类刻意没打 @Component：它们不依赖 Spring，单测里直接 new 就能用。
 * 由配置类装配，保证「领域逻辑与框架解耦」这件事在代码上是真的。
 */
@Configuration
public class WorkflowConfig {

    @Bean
    public PromptBuilder promptBuilder() {
        return new PromptBuilder();
    }

    @Bean
    public QualityGate qualityGate() {
        return new QualityGate();
    }
}

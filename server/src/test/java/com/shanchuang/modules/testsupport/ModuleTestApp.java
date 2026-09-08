package com.shanchuang.modules.testsupport;

import com.shanchuang.common.config.MybatisPlusConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 四个业务模块的测试上下文。
 *
 * 刻意不用 ShanchuangApplication：那会把 harness 与其它模块一起拉起来，
 * 单测只需要这四个包 + JwtUtil。测试类一律显式指定 classes = ModuleTestApp.class，
 * 所以它也不会被别的包下的 @SpringBootTest 误当成配置类。
 *
 * 但分页拦截器必须显式引进来：少了它 selectPage 不分页、total 恒为 0，
 * 而这个故障在生产上真实发生过一次——测试装配与生产不一致，测试就永远发现不了。
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@Import(MybatisPlusConfig.class)
@ComponentScan(basePackages = {
        "com.shanchuang.common.security",
        "com.shanchuang.modules.auth",
        "com.shanchuang.modules.persona",
        "com.shanchuang.modules.options",
        "com.shanchuang.modules.credit"
})
@MapperScan({
        "com.shanchuang.modules.auth.mapper",
        "com.shanchuang.modules.persona.mapper",
        "com.shanchuang.modules.options.mapper",
        "com.shanchuang.modules.credit.mapper"
})
public class ModuleTestApp {
}

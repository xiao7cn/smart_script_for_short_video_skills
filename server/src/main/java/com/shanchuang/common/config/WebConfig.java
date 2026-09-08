package com.shanchuang.common.config;

import com.shanchuang.common.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 免登录白名单，对应 docs/接口设计.md 的鉴权说明 */
    private static final String[] WHITELIST = {
            "/api/auth/sms/code",
            "/api/auth/sms/login",
            "/api/auth/wechat/login",
            "/api/options",
            "/error"
    };

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(WHITELIST);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发期 H5 跑在 5173，生产由 Nginx 同源转发，这里只放开本地来源
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

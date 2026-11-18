package io.interviewcodi.backend.auth.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code /api/**}는 로그인이 필요하다. 상태 확인과 로그아웃만 로그인 없이 호출할 수 있다.
 * 로그아웃은 이미 세션이 끝났어도 같은 결과(204)를 주기 위해 제외한다.
 */
@Configuration(proxyBeanMethods = false)
class AuthWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginRequiredInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/logout");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}

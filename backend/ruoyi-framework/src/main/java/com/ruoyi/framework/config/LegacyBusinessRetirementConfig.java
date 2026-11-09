package com.ruoyi.framework.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 旧模块重写期间阻止旧入口执行；恢复说明见 docs/20261106-旧模块停用与本地业务清空.md。 */
@Configuration
public class LegacyBusinessRetirementConfig implements WebMvcConfigurer, HandlerInterceptor {
    private static final String[] PATHS = {"work-order", "warehouse", "warehouse-order", "inventory",
            "acid-wash", "process-batch", "warehouse-flows", "route-skip-permits"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).order(-200);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getServletPath();
        for (String name : PATHS) {
            String prefix = "/metal/" + name;
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":503,\"msg\":\"该模块正在重构，暂不可用\"}");
                return false;
            }
        }
        return true;
    }
}

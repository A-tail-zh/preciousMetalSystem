package com.ruoyi.framework.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 旧模块重写期间阻止旧入口执行；恢复说明见 docs/20261106-旧模块停用与本地业务清空.md。 */
@Configuration
@Order(-90)
public class LegacyBusinessRetirementConfig extends OncePerRequestFilter implements WebMvcConfigurer, HandlerInterceptor {
    private static final String[] PATHS = {"work-order", "warehouse", "warehouse-order", "inventory",
            "acid-wash", "process-batch", "warehouse-flows", "route-skip-permits"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).order(-200);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return !rejectRetired(request,response);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        // Reject before handler mapping can turn an unknown retired method into a routing error.
        if(!rejectRetired(request,response))chain.doFilter(request,response);
    }

    private boolean rejectRetired(HttpServletRequest request,HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        for (String name : PATHS) {
            String prefix = "/metal/" + name;
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":503,\"msg\":\"该模块正在重构，暂不可用\"}");
                return true;
            }
        }
        return false;
    }
}

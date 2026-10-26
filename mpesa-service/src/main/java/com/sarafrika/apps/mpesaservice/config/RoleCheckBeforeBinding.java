package com.sarafrika.apps.mpesaservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Checks a handler's role-only {@code @PreAuthorize} before its arguments are bound and validated,
 * so a caller without the role gets 403 rather than a 400 naming the body's fields. Method security
 * still runs afterwards; expressions that read arguments ({@code #...}) are left to it alone.
 */
@Configuration
public class RoleCheckBeforeBinding implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new Interceptor()).addPathPatterns("/api/v1/admin/**", "/api/v1/mpesa/**");
    }

    static final class Interceptor implements HandlerInterceptor {

        private final PreAuthorizeAuthorizationManager manager = new PreAuthorizeAuthorizationManager();

        Interceptor() {
            DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
            handler.setRoleHierarchy(KeycloakRealmRoles.hierarchy());
            manager.setExpressionHandler(handler);
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!(handler instanceof HandlerMethod method)) {
                return true;
            }
            PreAuthorize rule = rule(method);
            if (rule == null || rule.value().contains("#")) {
                return true;
            }
            Authentication caller = SecurityContextHolder.getContext().getAuthentication();
            Method target = method.getMethod();
            AuthorizationResult result = manager.authorize(() -> caller,
                    new SimpleMethodInvocation(method.getBean(), target));
            if (result != null && !result.isGranted()) {
                throw new AccessDeniedException("Access Denied");
            }
            return true;
        }

        private static PreAuthorize rule(HandlerMethod method) {
            PreAuthorize onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), PreAuthorize.class);
            return onMethod != null ? onMethod
                    : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PreAuthorize.class);
        }
    }
}

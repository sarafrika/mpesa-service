package com.sarafrika.apps.mpesaservice.services.products;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Counts calls to /api/v1/mpesa/** by products (not console users) for the Products page. */
// The recorder is looked up lazily so @WebMvcTest slices, which have no repositories, skip counting.
@Configuration
public class ApiUsageWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<ApiUsageRecorder> recorder;

    public ApiUsageWebConfig(ObjectProvider<ApiUsageRecorder> recorder) {
        this.recorder = recorder;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiUsageInterceptor(recorder)).addPathPatterns("/api/v1/mpesa/**");
    }

    @Slf4j
    static class ApiUsageInterceptor implements HandlerInterceptor {

        private final ObjectProvider<ApiUsageRecorder> recorder;

        ApiUsageInterceptor(ObjectProvider<ApiUsageRecorder> recorder) {
            this.recorder = recorder;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                    Exception ex) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                    || !authentication.isAuthenticated() || CallerIdentity.isConsoleUser(authentication)) {
                return;
            }
            ApiUsageRecorder usage = recorder.getIfAvailable();
            if (usage == null) {
                return;
            }
            try {
                usage.record(CallerIdentity.clientId(authentication), ex != null || response.getStatus() >= 400);
            } catch (RuntimeException e) {
                log.warn("Could not count an API call: {}", e.getMessage());
            }
        }
    }
}

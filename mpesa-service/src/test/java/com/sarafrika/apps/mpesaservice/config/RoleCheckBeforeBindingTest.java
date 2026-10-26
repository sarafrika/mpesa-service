package com.sarafrika.apps.mpesaservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

/** Every write endpoint refuses a caller without its role before the body is read. */
class RoleCheckBeforeBindingTest {

    private final RoleCheckBeforeBinding.Interceptor interceptor = new RoleCheckBeforeBinding.Interceptor();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyGuardedWriteEndpointRefusesACallerWithoutTheRole() throws Exception {
        List<Method> writes = guardedWrites();
        assertThat(writes).hasSizeGreaterThan(40);
        for (Method write : writes) {
            String role = role(write);
            signIn(role.equals("mpesa-viewer") ? "nothing" : "mpesa-viewer");
            assertThatThrownBy(() -> preHandle(write)).as(write.toString()).isInstanceOf(AccessDeniedException.class);
            signIn(role);
            assertThat(preHandle(write)).as(write.toString()).isTrue();
        }
    }

    @Test
    void theHierarchyStillApplies() throws Exception {
        Method write = guardedWrites().stream().filter(m -> role(m).equals("mpesa-finance")).findFirst().orElseThrow();
        signIn("mpesa-admin");
        assertThat(preHandle(write)).isTrue();
        signIn("mpesa-operator");
        assertThatThrownBy(() -> preHandle(write)).isInstanceOf(AccessDeniedException.class);
    }

    private boolean preHandle(Method method) throws Exception {
        Object bean = mock(method.getDeclaringClass());
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new HandlerMethod(bean, method));
    }

    private static void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "x", "ROLE_" + role));
    }

    private static String role(Method method) {
        PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PreAuthorize.class);
        }
        return rule.value().replaceAll(".*hasRole\\('([^']+)'\\).*", "$1");
    }

    private static List<Method> guardedWrites() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Method> writes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.sarafrika.apps.mpesaservice.controllers.admin")) {
            if (candidate.getBeanClassName().contains("Test")) {
                continue;
            }
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                boolean write = method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class) || method.isAnnotationPresent(DeleteMapping.class);
                if (write) {
                    assertThat(AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                            || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), PreAuthorize.class))
                            .as("%s has no @PreAuthorize", method).isTrue();
                    writes.add(method);
                }
            }
        }
        return writes;
    }
}

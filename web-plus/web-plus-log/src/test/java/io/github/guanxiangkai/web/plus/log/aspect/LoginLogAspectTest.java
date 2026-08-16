package io.github.guanxiangkai.web.plus.log.aspect;

import io.github.guanxiangkai.web.plus.log.annotation.LoginLog;
import io.github.guanxiangkai.web.plus.log.entity.BaseLog;
import io.github.guanxiangkai.web.plus.log.spi.LoginLogHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import io.github.guanxiangkai.web.plus.core.net.ClientIpResolver;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginLogAspectTest {

    @Test
    void shouldNeverTreatBareStringArgumentAsUsername() throws Throwable {
        AtomicReference<BaseLog> captured = new AtomicReference<>();
        LoginLogAspect aspect = aspect(captured::set);
        ProceedingJoinPoint joinPoint = joinPoint("refresh-token-secret");

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation());
        result.block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUsername()).isEqualTo("unknown");
        assertThat(captured.get().getUsername()).doesNotContain("refresh-token-secret");
    }

    @Test
    void shouldReadOnlyExplicitUsernameAccessor() throws Throwable {
        AtomicReference<BaseLog> captured = new AtomicReference<>();
        LoginLogAspect aspect = aspect(captured::set);
        ProceedingJoinPoint joinPoint = joinPoint(new LoginCommand("example-user"));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation());
        result.block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getUsername()).isEqualTo("example-user");
    }

    @Test
    void shouldPersistOnlyExceptionTypeForFailure() throws Throwable {
        AtomicReference<BaseLog> captured = new AtomicReference<>();
        LoginLogAspect aspect = aspect(captured::set);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new LoginCommand("example-user")});
        when(joinPoint.proceed()).thenReturn(Mono.error(
                new IllegalStateException("database-endpoint-detail")));

        Mono<?> result = (Mono<?>) aspect.around(joinPoint, annotation());

        assertThatThrownBy(result::block).isInstanceOf(IllegalStateException.class);
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getMessage()).isEqualTo("IllegalStateException");
        assertThat(captured.get().getMessage()).doesNotContain("endpoint-detail");
    }

    private static LoginLogAspect aspect(LoginLogHandler handler) {
        LoginLogAspect aspect = new LoginLogAspect();
        ReflectionTestUtils.setField(aspect, "loginLogHandler", handler);
        ReflectionTestUtils.setField(aspect, "clientIpResolver", ClientIpResolver.directPeer());
        return aspect;
    }

    private static ProceedingJoinPoint joinPoint(Object argument) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{argument});
        when(joinPoint.proceed()).thenReturn(Mono.just("ok"));
        return joinPoint;
    }

    private static LoginLog annotation() {
        try {
            return LoginLogAspectTest.class
                    .getDeclaredMethod("annotatedMethod")
                    .getAnnotation(LoginLog.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("测试夹具缺少登录日志注解方法", exception);
        }
    }

    @LoginLog(entity = TestLoginLog.class, action = "REFRESH_TOKEN")
    private static void annotatedMethod() {
    }

    public record LoginCommand(String username) {
    }

    public static final class TestLoginLog extends BaseLog {
    }
}

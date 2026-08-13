package io.github.guanxiangkai.web.plus.web.context;

import io.github.guanxiangkai.web.plus.core.context.CurrentUser;
import io.github.guanxiangkai.web.plus.core.context.CurrentUserHolder;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * 将 web-plus {@link CurrentUserHolder} 注册到 Micrometer Context Propagation。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public class WebPlusCurrentUserThreadLocalAccessor implements ThreadLocalAccessor<CurrentUser> {

    @Override
    public Object key() {
        return CurrentUserHolder.REACTOR_CONTEXT_KEY;
    }

    @Override
    public CurrentUser getValue() {
        return CurrentUserHolder.get();
    }

    @Override
    public void setValue(CurrentUser value) {
        CurrentUserHolder.set(value);
    }

    @Override
    public void setValue() {
        CurrentUserHolder.clear();
    }
}

package io.github.guanxiangkai.web.plus.core.entity;

/**
 * 置顶能力接口。
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public interface Pinnable {

    PinInfo getPinInfo();

    void setPinInfo(PinInfo pinInfo);

    default Boolean getPinned() {
        PinInfo pinInfo = getPinInfo();
        return pinInfo == null ? null : pinInfo.getPinned();
    }

    default void setPinned(Boolean pinned) {
        PinInfo pinInfo = getPinInfo();
        if (pinInfo == null) {
            pinInfo = new PinInfo();
            setPinInfo(pinInfo);
        }
        pinInfo.setPinned(pinned);
    }
}

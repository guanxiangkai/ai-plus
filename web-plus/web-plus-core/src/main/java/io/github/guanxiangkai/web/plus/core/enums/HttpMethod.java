package io.github.guanxiangkai.web.plus.core.enums;

import java.util.Map;

/**
 * HTTP 方法枚举
 * <p>
 * 提供 O(1) 的 code 查找，以及「是否为修改类请求」的快速判断（供防抖拦截器等场景使用）。
 * </p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum HttpMethod implements BaseEnum<String> {

    GET("GET", "查询", false),
    POST("POST", "创建", true),
    PUT("PUT", "更新", true),
    DELETE("DELETE", "删除", true),
    PATCH("PATCH", "部分更新", true),
    HEAD("HEAD", "获取头信息", false),
    OPTIONS("OPTIONS", "预检请求", false);

    private static final Map<String, HttpMethod> CODE_MAP =
            BaseEnum.createCodeMap(HttpMethod.class, HttpMethod::getCode);

    private final String code;
    private final String description;
    private final boolean modifying;

    HttpMethod(String code, String description, boolean modifying) {
        this.code = code;
        this.description = description;
        this.modifying = modifying;
    }

    /**
     * 根据方法名称查找枚举（大小写不敏感，O(1)）
     */
    public static HttpMethod fromCode(String code) {
        return CODE_MAP.get(code != null ? code.toUpperCase() : null);
    }

    /**
     * 判断指定方法是否为修改类（POST / PUT / DELETE / PATCH）
     */
    public static boolean isModifyingMethod(String method) {
        HttpMethod httpMethod = fromCode(method);
        return httpMethod != null && httpMethod.isModifying();
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 是否为修改类方法（POST / PUT / DELETE / PATCH）
     */
    public boolean isModifying() {
        return modifying;
    }
}


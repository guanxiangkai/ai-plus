package io.github.guanxiangkai.web.plus.security.token;

/**
 * 请求参数令牌的来源。
 */
public enum RequestParameterTokenSource {

    /** URL 查询参数。 */
    QUERY,

    /** JSON 请求体顶层字段。 */
    BODY,

    /** 请求中未提供令牌。 */
    NONE
}

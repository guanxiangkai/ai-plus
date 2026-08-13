package io.github.guanxiangkai.web.plus.core.constants;

import java.time.Duration;

/**
 * 认证常量
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public final class AuthConstants {

    private AuthConstants() {
        throw new UnsupportedOperationException("这是一个效用类，无法实例化");
    }


    /**
     * Token相关常量
     */
    public static final class TokenConstants {

        /**
         * Token缓存名称
         */
        public static final String TOKEN_CACHE = "auth:token";

        /**
         * 刷新Token缓存名称
         */
        public static final String REFRESH_TOKEN_CACHE = "auth:refresh";

        /**
         * Token黑名单缓存名称
         */
        public static final String BLACKLIST_CACHE = "auth:blacklist";

        private TokenConstants() {
        }
    }

    /**
     * 用户认证概要缓存常量。
     */
    public static final class UserAuthCacheConstants {

        /**
         * 用户认证 Hash 前缀，完整键：{@code user:auth:{userId}}。
         */
        public static final String USER_AUTH_CACHE_PREFIX = "user:auth:";

        /**
         * 用户名到用户 ID 的索引前缀，完整键：{@code user:auth:username:{username}}。
         */
        public static final String USERNAME_INDEX_PREFIX = "user:auth:username:";

        public static final String FIELD_ID = "id";
        public static final String FIELD_USERNAME = "username";
        public static final String FIELD_PASSWORD_HASH = "passwordHash";
        public static final String FIELD_ENABLED = "enabled";
        public static final String FIELD_TOKEN_VERSION = "tokenVersion";
        public static final String FIELD_NICKNAME = "nickname";
        public static final String FIELD_AVATAR = "avatar";
        public static final String FIELD_USER_TYPE = "userType";
        public static final String FIELD_SUPER_ADMIN = "superAdmin";
        public static final String FIELD_TENANT_ID = "tenantId";
        public static final String FIELD_DEPT_ID = "deptId";
        public static final String FIELD_ROLE_CODES = "roleCodes";
        public static final String FIELD_POST_CODES = "postCodes";
        public static final String FIELD_PERMISSIONS = "permissions";
        public static final String FIELD_DEPT_IDS = "deptIds";

        private UserAuthCacheConstants() {
        }
    }

    /**
     * 网关透传请求头常量
     */
    public static final class HeaderConstants {

        /**
         * 用户 ID 请求头
         */
        public static final String USER_ID = "X-User-Id";

        /**
         * 租户 ID 请求头
         */
        public static final String TENANT_ID = "X-Tenant-Id";

        /**
         * 用户 Claims 请求头，网关默认写入 UTF-8 Base64URL 编码后的 JSON
         */
        public static final String USER_CLAIMS = "X-User-Claims";

        /**
         * 用户 Claims 编码方式请求头
         */
        public static final String USER_CLAIMS_ENCODING = "X-User-Claims-Encoding";

        /**
         * 网关/内部服务到下游服务的受信任转发令牌请求头
         */
        public static final String TRUSTED_FORWARD_TOKEN = "X-Trusted-Forward-Token";

        /**
         * 内部服务调用使用的固定主体标识。
         */
        public static final String INTERNAL_SERVICE_USER_ID = "internal-service";

        private HeaderConstants() {
        }
    }

    /**
     * 岗位相关常量
     */
    public static final class PostConstants {

        /**
         * 用户选中岗位缓存键前缀（Redis）
         * <p>完整键：{@code user:selectedPost:{userId}}</p>
         */
        public static final String SELECTED_POST_CACHE = "user:selectedPost:";

        /**
         * 选中岗位缓存 TTL（100 年，等效永不过期）
         */
        public static final Duration SELECTED_POST_TTL = Duration.ofDays(36500);

        private PostConstants() {
        }
    }

    /**
     * 部门相关常量
     */
    public static final class DeptConstants {

        /**
         * 用户选中部门缓存键前缀（Redis）
         * <p>完整键：{@code user:selectedDept:{userId}}</p>
         */
        public static final String SELECTED_DEPT_CACHE = "user:selectedDept:";

        /**
         * 选中部门缓存 TTL（100 年，等效永不过期）
         */
        public static final Duration SELECTED_DEPT_TTL = Duration.ofDays(36500);

        private DeptConstants() {
        }
    }



}

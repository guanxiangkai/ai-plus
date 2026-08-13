package io.github.guanxiangkai.jpa.plus.interceptor.logicdelete.enums;

/**
 * 内置逻辑删除值策略枚举
 *
 * <p>覆盖最常见的逻辑删除标识方案。</p>
 *
 * @author guanxiangkai
 * @since 1.0.0
 */
public enum LogicDeleteType implements LogicDeleteValue {

    /**
     * Integer 类型：0=未删除，1=已删除
     */
    INTEGER {
        @Override
        public Object deletedValue() {
            return 1;
        }

        @Override
        public Object notDeletedValue() {
            return 0;
        }

        @Override
        public Class<?> javaType() {
            return Integer.class;
        }
    },

    /**
     * Boolean 类型：false=未删除，true=已删除
     */
    BOOLEAN {
        @Override
        public Object deletedValue() {
            return true;
        }

        @Override
        public Object notDeletedValue() {
            return false;
        }

        @Override
        public Class<?> javaType() {
            return Boolean.class;
        }
    },

    /**
     * String 类型："0"=未删除，"1"=已删除
     */
    STRING {
        @Override
        public Object deletedValue() {
            return "1";
        }

        @Override
        public Object notDeletedValue() {
            return "0";
        }

        @Override
        public Class<?> javaType() {
            return String.class;
        }
    },

    /**
     * String 类型："N"=未删除，"Y"=已删除
     */
    YES_NO {
        @Override
        public Object deletedValue() {
            return "Y";
        }

        @Override
        public Object notDeletedValue() {
            return "N";
        }

        @Override
        public Class<?> javaType() {
            return String.class;
        }
    }
}


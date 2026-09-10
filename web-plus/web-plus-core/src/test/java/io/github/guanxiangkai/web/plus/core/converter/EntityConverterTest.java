package io.github.guanxiangkai.web.plus.core.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class EntityConverterTest {

    @Test
    void toEntityShouldCopyRecordComponents() {
        TestEntity entity = EntityConverter.toEntity(new TestDto("new-name", 10), TestEntity.class);

        assertThat(entity.getName()).isEqualTo("new-name");
        assertThat(entity.getScore()).isEqualTo(10);
    }

    @Test
    void updateEntityShouldCopyRecordComponentsAndSkipNullValues() {
        TestEntity entity = new TestEntity();
        entity.setName("old-name");
        entity.setScore(1);

        EntityConverter.updateEntity(new TestDto("new-name", null), entity);

        assertThat(entity.getName()).isEqualTo("new-name");
        assertThat(entity.getScore()).isEqualTo(1);
    }

    @Test
    void registerShouldUseDirectConverterInBothDirections() {
        EntityConverter.register(new DirectConverter());

        assertThat(EntityConverter.convert(new DirectSource("forward"), DirectTarget.class).value()).isEqualTo("to-target:forward");
        assertThat(EntityConverter.convert(new DirectTarget("reverse"), DirectSource.class).value()).isEqualTo("to-source:reverse");
    }

    @Test
    void registerShouldKeepForwardConversionForSameSourceAndTargetType() {
        EntityConverter.register(new SameTypeConverter());

        assertThat(EntityConverter.convert(new SameType("value"), SameType.class).value()).isEqualTo("forward:value");
    }

    @Test
    void registerShouldResolveTypesFromAbstractGenericSuperclass() {
        EntityConverter.register(new AbstractInheritedConverter());

        assertThat(EntityConverter.convert(new AbstractSource("value"), AbstractTarget.class).value()).isEqualTo("abstract:value");
    }

    @Test
    void registerShouldResolveTypesFromExtendedInterface() {
        EntityConverter.register(new ExtendedInterfaceConverter());

        assertThat(EntityConverter.convert(new InterfaceSource("value"), InterfaceTarget.class).value()).isEqualTo("interface:value");
    }

    @Test
    void registerShouldResolveTypesAcrossMultipleGenericInheritanceLevels() {
        EntityConverter.register(new MultiLevelConverter());

        assertThat(EntityConverter.convert(new MultiLevelSource("value"), MultiLevelTarget.class).value()).isEqualTo("multi:value");
    }

    @Test
    void convertShouldExposeMissingReverseConverterImplementation() {
        EntityConverter.register(new ForwardOnlyConverter());

        assertThatIllegalStateException()
                .isThrownBy(() -> EntityConverter.convert(new ForwardOnlyTarget("value"), ForwardOnlySource.class))
                .withMessageContaining("已注册 TypeConverter 转换失败")
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void convertShouldNotFallBackToBeanUtilsWhenRegisteredConverterFails() {
        EntityConverter.register(new FailingConverter());

        assertThatIllegalStateException()
                .isThrownBy(() -> EntityConverter.convert(new FailingSource("value"), FailingTarget.class))
                .withMessageContaining("已注册 TypeConverter 转换失败")
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("对象转换失败");
    }

    @Test
    void registerShouldRejectUnresolvedGenericConverterTypes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityConverter.register(new UnresolvedConverter<>()))
                .withMessageContaining("必须解析为具体类型");
    }

    @Test
    void registerShouldRejectBoundedButUnresolvedGenericConverterTypes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityConverter.register(new BoundedUnresolvedConverter<>()))
                .withMessageContaining("必须解析为具体类型");
    }

    @Test
    void registerShouldRejectParameterizedConverterEndpoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EntityConverter.register(new ListConverter()))
                .withMessageContaining("TypeConverter");
    }

    private record TestDto(String name, Integer score) {
    }

    private static final class TestEntity {
        private String name;
        private Integer score;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }
    }

    private record DirectSource(String value) {
    }

    private record DirectTarget(String value) {
    }

    private static final class DirectConverter implements TypeConverter<DirectSource, DirectTarget> {
        @Override
        public DirectTarget convert(DirectSource source) {
            return new DirectTarget("to-target:" + source.value());
        }

        @Override
        public DirectSource convertBack(DirectTarget target) {
            return new DirectSource("to-source:" + target.value());
        }
    }

    private record SameType(String value) {
    }

    private static final class SameTypeConverter implements TypeConverter<SameType, SameType> {
        @Override
        public SameType convert(SameType source) {
            return new SameType("forward:" + source.value());
        }

        @Override
        public SameType convertBack(SameType target) {
            return new SameType("reverse:" + target.value());
        }
    }

    private record AbstractSource(String value) {
    }

    private record AbstractTarget(String value) {
    }

    private abstract static class AbstractGenericConverter<S, T> implements TypeConverter<S, T> {
    }

    private static final class AbstractInheritedConverter extends AbstractGenericConverter<AbstractSource, AbstractTarget> {
        @Override
        public AbstractTarget convert(AbstractSource source) {
            return new AbstractTarget("abstract:" + source.value());
        }
    }

    private record InterfaceSource(String value) {
    }

    private record InterfaceTarget(String value) {
    }

    private interface ExtendedConverter extends TypeConverter<InterfaceSource, InterfaceTarget> {
    }

    private static final class ExtendedInterfaceConverter implements ExtendedConverter {
        @Override
        public InterfaceTarget convert(InterfaceSource source) {
            return new InterfaceTarget("interface:" + source.value());
        }
    }

    private record MultiLevelSource(String value) {
    }

    private record MultiLevelTarget(String value) {
    }

    private abstract static class FirstGenericLevel<S, T> implements TypeConverter<S, T> {
    }

    private abstract static class SecondGenericLevel<T> extends FirstGenericLevel<MultiLevelSource, T> {
    }

    private static final class MultiLevelConverter extends SecondGenericLevel<MultiLevelTarget> {
        @Override
        public MultiLevelTarget convert(MultiLevelSource source) {
            return new MultiLevelTarget("multi:" + source.value());
        }
    }

    private record ForwardOnlySource(String value) {
    }

    private record ForwardOnlyTarget(String value) {
    }

    private static final class ForwardOnlyConverter implements TypeConverter<ForwardOnlySource, ForwardOnlyTarget> {
        @Override
        public ForwardOnlyTarget convert(ForwardOnlySource source) {
            return new ForwardOnlyTarget(source.value());
        }
    }

    private record FailingSource(String value) {
    }

    private static final class FailingTarget {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private static final class FailingConverter implements TypeConverter<FailingSource, FailingTarget> {
        @Override
        public FailingTarget convert(FailingSource source) {
            throw new IllegalStateException("converter failure");
        }
    }

    private static final class UnresolvedConverter<S, T> implements TypeConverter<S, T> {
        @Override
        public T convert(S source) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class BoundedUnresolvedConverter<S extends Number, T extends CharSequence>
            implements TypeConverter<S, T> {
        @Override
        public T convert(S source) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ListConverter implements TypeConverter<List<DirectSource>, List<DirectTarget>> {
        @Override
        public List<DirectTarget> convert(List<DirectSource> source) {
            throw new UnsupportedOperationException();
        }
    }
}

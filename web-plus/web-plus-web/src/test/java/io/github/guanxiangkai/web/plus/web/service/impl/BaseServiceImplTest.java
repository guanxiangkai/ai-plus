package io.github.guanxiangkai.web.plus.web.service.impl;

import io.github.guanxiangkai.web.plus.core.entity.*;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.core.spi.ResponseTranslator;
import io.github.guanxiangkai.web.plus.web.repository.BaseRepository;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BaseServiceImplTest {

    @Test
    void shouldReuseReadOnlyRepositoryQueryBase() {
        assertThat(ReadOnlyBaseServiceImpl.class.isAssignableFrom(BaseServiceImpl.class)).isTrue();
    }

    @Test
    void shouldNotExposeUnboundedAllServiceMethod() {
        assertThatThrownBy(() -> IBaseService.class.getMethod("all"))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> BaseServiceImpl.class.getMethod("all"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void listShouldUseNonNullSpecificationByDefault() {
        BaseRepository<String, String, TestEntity> repository = mockRepository();
        TestService service = new TestService(repository);
        ArgumentCaptor<Specification<TestEntity>> specCaptor = specCaptor();

        service.list(null);

        verify(repository).findPageVo(isNull(), specCaptor.capture(), any(Sort.class));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    void listShouldUseTranslatedRecordsFromImmutablePageAndPreserveMetadata() {
        PageResponse<ResponseValue> sourcePage = new PageResponse<>(
                List.of(new ResponseValue("原始")), 41, 3, 20, 9
        );
        BaseRepository<ResponseValue, ResponseValue, TestEntity> repository = mockRepository(TestEntity.class);
        when(repository.findPageVo(nullable(PageQuery.class), any(Specification.class), any(Sort.class)))
                .thenReturn(sourcePage);
        ResponseValueService service = new ResponseValueService(repository);
        service.setResponseTranslators(List.of(translator(0, value -> value + "-已转换")));

        PageResponse<ResponseValue> result = service.list(null);

        assertThat(result.records()).containsExactly(new ResponseValue("原始-已转换"));
        assertThat(sourcePage.records()).containsExactly(new ResponseValue("原始"));
        assertThat(result.total()).isEqualTo(41);
        assertThat(result.pageNum()).isEqualTo(3);
        assertThat(result.pageSize()).isEqualTo(20);
        assertThat(result.pages()).isEqualTo(9);
    }

    @Test
    void listShouldApplyResponseTranslatorsInOrder() {
        BaseRepository<ResponseValue, ResponseValue, TestEntity> repository = mockRepository(TestEntity.class);
        when(repository.findPageVo(nullable(PageQuery.class), any(Specification.class), any(Sort.class)))
                .thenReturn(PageResponse.of(List.of(new ResponseValue("原始")), 1, 1, 10));
        ResponseValueService service = new ResponseValueService(repository);
        service.setResponseTranslators(List.of(
                translator(20, value -> value + "-第二步"),
                translator(10, value -> value + "-第一步")
        ));

        PageResponse<ResponseValue> result = service.list(null);

        assertThat(result.records()).containsExactly(new ResponseValue("原始-第一步-第二步"));
    }

    @Test
    void listShouldRetainRecordsWhenNoResponseTranslatorExists() {
        BaseRepository<ResponseValue, ResponseValue, TestEntity> repository = mockRepository(TestEntity.class);
        PageResponse<ResponseValue> sourcePage = PageResponse.of(List.of(new ResponseValue("原始")), 1, 1, 10);
        when(repository.findPageVo(nullable(PageQuery.class), any(Specification.class), any(Sort.class)))
                .thenReturn(sourcePage);

        PageResponse<ResponseValue> result = new ResponseValueService(repository).list(null);

        assertThat(result.records()).containsExactlyElementsOf(sourcePage.records());
        assertThat(result.total()).isEqualTo(sourcePage.total());
        assertThat(result.pageNum()).isEqualTo(sourcePage.pageNum());
        assertThat(result.pageSize()).isEqualTo(sourcePage.pageSize());
        assertThat(result.pages()).isEqualTo(sourcePage.pages());
    }

    @SuppressWarnings("unchecked")
    private static BaseRepository<String, String, TestEntity> mockRepository() {
        return mockRepository(TestEntity.class);
    }

    @SuppressWarnings("unchecked")
    private static <LV, DV, E extends BaseEntity> BaseRepository<LV, DV, E> mockRepository(Class<E> entityClass) {
        BaseRepository<LV, DV, E> repository = mock(BaseRepository.class);
        when(repository.entityClass()).thenReturn(entityClass);
        when(repository.findPageVo(nullable(PageQuery.class), any(Specification.class), any(Sort.class)))
                .thenReturn(PageResponse.<LV>empty());
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static ResponseTranslator translator(int order, UnaryOperator<String> translateValue) {
        return new ResponseTranslator() {
            @Override
            public <T> T translate(T value) {
                ResponseValue responseValue = (ResponseValue) value;
                return (T) new ResponseValue(translateValue.apply(responseValue.value()));
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Specification<TestEntity>> specCaptor() {
        return ArgumentCaptor.forClass((Class) Specification.class);
    }

    @Test
    void requestedSortOrderShouldResolveEmbeddedSortInfoPath() {
        BaseRepository<String, String, SortableEntity> repository = mockRepository(SortableEntity.class);
        SortableService service = new SortableService(repository);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);

        service.list(new TestPageQuery("sortOrder", "ASC"));

        verify(repository).findPageVo(any(TestPageQuery.class), any(Specification.class), sortCaptor.capture());
        assertThat(sortCaptor.getValue().getOrderFor("sortInfo.sortOrder")).isNotNull();
        assertThat(sortCaptor.getValue().getOrderFor("sortOrder")).isNull();
    }

    @Test
    void requestedPinnedShouldResolveEmbeddedPinInfoPath() {
        BaseRepository<String, String, PinnableEntity> repository = mockRepository(PinnableEntity.class);
        PinnableService service = new PinnableService(repository);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);

        service.list(new TestPageQuery("pinned", "DESC"));

        verify(repository).findPageVo(any(TestPageQuery.class), any(Specification.class), sortCaptor.capture());
        assertThat(sortCaptor.getValue().getOrderFor("pinInfo.pinned")).isNotNull();
        assertThat(sortCaptor.getValue().getOrderFor("pinned")).isNull();
    }

    private static final class TestService extends BaseServiceImpl<PageQuery, String, String, Void, Void, TestEntity> {
        private final BaseRepository<String, String, TestEntity> repository;

        private TestService(BaseRepository<String, String, TestEntity> repository) {
            this.repository = repository;
        }

        @Override
        protected BaseRepository<String, String, TestEntity> getRepository() {
            return repository;
        }
    }

    private static final class ResponseValueService
            extends ReadOnlyBaseServiceImpl<PageQuery, ResponseValue, ResponseValue, TestEntity> {
        private final BaseRepository<ResponseValue, ResponseValue, TestEntity> repository;

        private ResponseValueService(BaseRepository<ResponseValue, ResponseValue, TestEntity> repository) {
            this.repository = repository;
        }

        @Override
        protected BaseRepository<ResponseValue, ResponseValue, TestEntity> getRepository() {
            return repository;
        }
    }

    private static final class TestEntity extends BaseEntity {
    }

    private record ResponseValue(String value) {
    }

    private static final class SortableService extends BaseServiceImpl<TestPageQuery, String, String, Void, Void, SortableEntity> {
        private final BaseRepository<String, String, SortableEntity> repository;

        private SortableService(BaseRepository<String, String, SortableEntity> repository) {
            this.repository = repository;
        }

        @Override
        protected BaseRepository<String, String, SortableEntity> getRepository() {
            return repository;
        }
    }

    private static final class SortableEntity extends BaseEntity implements Sortable {
        private SortInfo sortInfo = new SortInfo();

        @Override
        public SortInfo getSortInfo() {
            return sortInfo;
        }

        @Override
        public void setSortInfo(SortInfo sortInfo) {
            this.sortInfo = sortInfo;
        }
    }

    private static final class PinnableService extends BaseServiceImpl<TestPageQuery, String, String, Void, Void, PinnableEntity> {
        private final BaseRepository<String, String, PinnableEntity> repository;

        private PinnableService(BaseRepository<String, String, PinnableEntity> repository) {
            this.repository = repository;
        }

        @Override
        protected BaseRepository<String, String, PinnableEntity> getRepository() {
            return repository;
        }
    }

    private static final class PinnableEntity extends BaseEntity implements Pinnable {
        private PinInfo pinInfo = new PinInfo();

        @Override
        public PinInfo getPinInfo() {
            return pinInfo;
        }

        @Override
        public void setPinInfo(PinInfo pinInfo) {
            this.pinInfo = pinInfo;
        }
    }

    private record TestPageQuery(String sortBy, String sortDir) implements PageQuery {
        @Override
        public int page() {
            return 1;
        }

        @Override
        public int size() {
            return 10;
        }
    }
}

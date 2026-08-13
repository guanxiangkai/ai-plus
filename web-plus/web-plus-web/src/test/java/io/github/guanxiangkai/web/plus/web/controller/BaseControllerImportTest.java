package io.github.guanxiangkai.web.plus.web.controller;

import io.github.guanxiangkai.web.plus.core.model.ApiResponse;
import io.github.guanxiangkai.web.plus.core.model.PageQuery;
import io.github.guanxiangkai.web.plus.core.model.PageResponse;
import io.github.guanxiangkai.web.plus.web.exception.ImportValidationException;
import io.github.guanxiangkai.web.plus.web.service.IBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseControllerImportTest {

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Test
    void importData_rejectsFilesLargerThanConfiguredLimit() {
        TestController controller = new TestController(4L);

        ApiResponse<?> response = (ApiResponse<?>) controller.importData(
                Mono.just(filePart("demo.xlsx", "hello"))
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.message()).contains("文件大小超出限制");
        assertThat(response.message()).contains("4 bytes");
    }

    @Test
    void importData_passesBytesToHandlerWithinLimit() {
        TestController controller = new TestController(8L);

        ApiResponse<?> response = (ApiResponse<?>) controller.importData(
                Mono.just(filePart("demo.xlsx", "hello"))
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).isEqualTo(5);
    }

    @Test
    void importData_returnsRowAndColumnValidationMessage() {
        TestController controller = new TestController(8L, true);

        ApiResponse<?> response = (ApiResponse<?>) controller.importData(
                Mono.just(filePart("demo.xlsx", "hello"))
        ).block();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.message()).contains("第2行第F(计划开始时间)列");
        assertThat(response.message()).contains("日期格式不正确");
        assertThat(response.data()).isInstanceOf(List.class);
        assertThat((List<?>) response.data()).hasSize(1);
    }

    @Test
    void shouldExposeSpelMetadataThroughBaseController() {
        TestController controller = new TestController(8L);

        assertThat(controller.getPermissionPrefix()).isEqualTo("web:test");
        assertThat(controller.getModuleName()).isEqualTo("Web");
        assertThat(controller.getEntityName()).isEqualTo("Test");
    }

    private FilePart filePart(String filename, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        int split = Math.min(2, bytes.length);
        DataBuffer first = bufferFactory.wrap(Arrays.copyOfRange(bytes, 0, split));
        DataBuffer second = bufferFactory.wrap(Arrays.copyOfRange(bytes, split, bytes.length));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new FilePart() {
            @Override
            public String filename() {
                return filename;
            }

            @Override
            public Mono<Void> transferTo(Path dest) {
                return Mono.error(new UnsupportedOperationException());
            }

            @Override
            public String name() {
                return "file";
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public Flux<DataBuffer> content() {
                return Flux.just(first, second);
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class TestController extends BaseController {

        private final long importMaxBytes;
        private final boolean validationError;

        private TestController(long importMaxBytes) {
            this(importMaxBytes, false);
        }

        private TestController(long importMaxBytes, boolean validationError) {
            this.importMaxBytes = importMaxBytes;
            this.validationError = validationError;
        }

        @Override
        protected IBaseService getService() {
            return new IBaseService<>() {
                @Override
                public PageResponse list(PageQuery query) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Object detail(String id) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String create(Object dto) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void update(String id, Object dto) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void delete(String id) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void batchDelete(List<String> ids) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void updateEnabled(String id, Boolean enabled) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void batchUpdateEnabled(List<String> ids, Boolean enabled) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        protected long getImportMaxBytes() {
            return importMaxBytes;
        }

        @Override
        protected int handleImport(byte[] fileBytes, String filename) {
            if (validationError) {
                throw new ImportValidationException(List.of(
                        new ImportValidationException.ImportError(
                                2, "F(计划开始时间)", "startTime", "日期格式不正确")
                ));
            }
            return fileBytes.length;
        }
    }

    private record TestQuery(int page, int size, String sortBy, String sortDir) implements PageQuery {
    }
}

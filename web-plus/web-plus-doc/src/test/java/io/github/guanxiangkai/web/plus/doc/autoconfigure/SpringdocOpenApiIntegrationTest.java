package io.github.guanxiangkai.web.plus.doc.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@SpringBootTest(
        classes = SpringdocOpenApiIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SpringdocOpenApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void shouldPublishWebPlusOpenApiDocumentWithDurationSchemaAndBearerSecurity() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("Web Plus API")
                .jsonPath("$.components.securitySchemes.Bearer.type").isEqualTo("http")
                .jsonPath("$.components.securitySchemes.Bearer.scheme").isEqualTo("bearer")
                .jsonPath("$.security[0].Bearer").isArray()
                .jsonPath("$.paths['/sample'].get.responses['200'].content['application/json'].schema.$ref")
                .isEqualTo("#/components/schemas/SampleResponse")
                .jsonPath("$.components.schemas.SampleResponse.properties.latency.type").isEqualTo("string")
                .jsonPath("$.components.schemas.SampleResponse.properties.latency.format").isEqualTo("duration")
                .jsonPath("$.components.schemas.SampleResponse.properties.label.type").isEqualTo("string");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SampleController.class)
    static class TestApplication {
    }

    @RestController
    static class SampleController {

        @GetMapping("/sample")
        SampleResponse sample() {
            return new SampleResponse(Duration.ofSeconds(1), "ready");
        }
    }

    record SampleResponse(Duration latency, String label) {
    }
}

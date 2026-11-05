package com.ethioloadai.document.storage;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStorageConfigurationTest {

    @Test
    void registersExactlyOneLocalStorageBeanForE2eProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("e2e");
            context.register(DocumentStorageConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(DocumentStorage.class))
                    .hasSize(1)
                    .allSatisfy((name, storage) ->
                            assertThat(storage).isInstanceOf(LocalDocumentStorage.class));
        }
    }

    @Test
    void productionUsesB2WithLocalLegacyReadFallbackAndNoNetworkCallsOnStartup() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b2-test", Map.of(
                    "b2.key-id", "test-key-id",
                    "b2.application-key", "test-application-key",
                    "b2.bucket-name", "private-test-bucket",
                    "b2.endpoint", "https://s3.us-west-004.backblazeb2.com",
                    "b2.region", "us-west-004",
                    "b2.legacy-local-path", "legacy-uploads"
            )));
            context.register(DocumentStorageConfiguration.class);
            context.refresh();

            assertThat(context.getBean(DocumentStorage.class)).isInstanceOf(LegacyDocumentStorage.class);
            assertThat(context.getBeansOfType(B2DocumentStorage.class)).hasSize(1);
        }
    }

    @Test
    void rejectsNonRegionalOrInsecureEndpoints() {
        assertThatThrownBy(() ->
                DocumentStorageConfiguration.validateEndpoint(
                        "http://s3.us-west-004.backblazeb2.com", "us-west-004"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                DocumentStorageConfiguration.validateEndpoint(
                        "https://s3.eu-central-003.backblazeb2.com", "us-west-004"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() ->
                DocumentStorageConfiguration.validateEndpoint(
                        "https://user@s3.us-west-004.backblazeb2.com", "us-west-004"))
                .isInstanceOf(IllegalStateException.class);
    }
}

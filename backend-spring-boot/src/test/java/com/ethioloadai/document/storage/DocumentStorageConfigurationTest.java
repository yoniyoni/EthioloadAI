package com.ethioloadai.document.storage;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

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
}

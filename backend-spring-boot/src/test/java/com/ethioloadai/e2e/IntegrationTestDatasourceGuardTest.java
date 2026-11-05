package com.ethioloadai.e2e;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.TestDatabaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationTestDatasourceGuardTest {

    private static final String BOOT_PROPERTY_MAPPING_PROCESSOR =
            "org.springframework.boot.test.autoconfigure.properties.PropertyMappingContextCustomizer"
                    + "$PropertyMappingCheckBeanPostProcessor";

    @Test
    void acceptsTheConfiguredInMemoryH2TestDatasource() {
        assertThatCode(() -> IntegrationTestDatasourceGuard.validateH2Environment(
                environment(testProperties()))).doesNotThrowAnyException();
    }

    @Test
    void rejectsExternalDatasourceOverrideEvenWhenH2HasHigherPrecedence() {
        MockEnvironment environment = environment(testProperties());
        environment.getPropertySources().addFirst(new MapPropertySource(
                "external-override",
                Map.of("spring.datasource.url", "jdbc:postgresql://localhost:5432/shared")));

        assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateH2Environment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url");
    }

    @Test
    void rejectsAlternateDatasourceAndFlywayOverrides() {
        for (String property : new String[]{
                "spring.datasource.hikari.jdbc-url",
                "spring.flyway.url",
                "spring.flyway.enabled"
        }) {
            Map<String, Object> properties = testProperties();
            properties.put(property, "spring.flyway.enabled".equals(property)
                    ? "true"
                    : "jdbc:postgresql://localhost/db");
            assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateH2Environment(
                    environment(properties)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void acceptsOnlyTheExactBootPropertyMappingProcessorAsAProgrammaticBeanPostProcessor()
            throws ReflectiveOperationException {
        Class<?> processorType = Class.forName(BOOT_PROPERTY_MAPPING_PROCESSOR);
        Constructor<?> constructor = processorType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object processor = constructor.newInstance();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton(BOOT_PROPERTY_MAPPING_PROCESSOR, processor);

        assertThat(processor).isInstanceOf(BeanPostProcessor.class);
        assertThatCode(() -> IntegrationTestDatasourceGuard.validateProcessorDefinitions(beanFactory))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> IntegrationTestDatasourceGuard.requireFrameworkProcessor(
                        beanFactory, BOOT_PROPERTY_MAPPING_PROCESSOR, "factory post-processor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("factory post-processor")
                .hasMessageContaining(BOOT_PROPERTY_MAPPING_PROCESSOR);
    }

    @Test
    void rejectsUnrelatedProgrammaticPostProcessorWithDiagnosticDetails() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("unapprovedProcessor", new UnapprovedBeanPostProcessor());

        assertThatThrownBy(() ->
                        IntegrationTestDatasourceGuard.validateProcessorDefinitions(beanFactory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bean post-processor")
                .hasMessageContaining("unapprovedProcessor")
                .hasMessageContaining(UnapprovedBeanPostProcessor.class.getName());
    }

    @Test
    void acceptsDefinitionBackedSpringFrameworkPostProcessor() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition(
                "frameworkProcessor",
                new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class));

        assertThatCode(() -> IntegrationTestDatasourceGuard.validateProcessorDefinitions(beanFactory))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDefinitionBackedCustomPostProcessor() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition(
                "customProcessor", new RootBeanDefinition(CustomBeanPostProcessor.class));

        assertThatThrownBy(() ->
                        IntegrationTestDatasourceGuard.validateProcessorDefinitions(beanFactory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("custom Spring post-processors");
    }

    @Test
    void acceptsStandardBootManagedH2Context() {
        bootManagedH2Runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class))
                    .isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
            IntegrationTestDatasourceGuard.validateHikariDatasource(
                    (com.zaxxer.hikari.HikariDataSource) context.getBean(DataSource.class));
        });
    }

    @Test
    void rejectsUnsupportedFinalDatasourceInstances() {
        DriverManagerDataSource externalDatasource =
                new DriverManagerDataSource("jdbc:postgresql://localhost:5432/shared");

        assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateDatasourceInstance(
                        "dataSource",
                        externalDatasource,
                        BootManagedH2Test.class,
                        environment(testProperties())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("final DataSource instance");
    }

    @Test
    void acceptsDataJpaTestEmbeddedH2Replacement() {
        dataJpaH2Runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isInstanceOf(EmbeddedDatabase.class);
        });
    }

    @Test
    void rejectsNonH2DataJpaTestReplacementAnnotation() {
        assertThatThrownBy(() ->
                IntegrationTestDatasourceGuard.validateDataJpaTestDatabaseAnnotation(
                        NonH2EmbeddedDataJpaTest.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-H2 embedded database");
    }

    @Test
    void rejectsExternalJdbcConnectionDetailsBeforeDatasourceCreation() {
        bootManagedH2Runner()
                .withUserConfiguration(ExternalJdbcConnectionDetailsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("custom JdbcConnectionDetails");
                });
    }

    @Test
    void rejectsCustomDatasourceDefinitionBeforeDatasourceCreation() {
        bootManagedH2Runner()
                .withUserConfiguration(CustomDatasourceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("DataSource definition");
                });
    }

    @Test
    void rejectsConflictingDatasourcePropertyInBootContext() {
        bootManagedH2Runner()
                .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/shared")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("spring.datasource.url");
                });
    }

    @Test
    void rejectsDynamicDatasourcePropertiesOnOrdinaryIntegrationTests() {
        assertThatThrownBy(() -> new IntegrationTestDatasourceGuard().createContextCustomizer(
                DynamicDatasourceTest.class, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DynamicPropertySource");
    }

    @Test
    void rejectsInheritedDynamicPropertyMethodsOnOrdinaryIntegrationTests() {
        assertThatThrownBy(() -> new IntegrationTestDatasourceGuard().createContextCustomizer(
                InheritedDynamicDatasourceTest.class, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DynamicPropertySource");
    }

    @Test
    void rejectsEnclosingDynamicPropertyMethodsRecognizedBySpringTest() {
        assertThatThrownBy(() -> new IntegrationTestDatasourceGuard().createContextCustomizer(
                DynamicPropertyEnclosingTest.EnclosedDynamicDatasourceTest.class, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DynamicPropertySource");
    }

    @Test
    void rejectsDynamicPropertyMethodsOnInterfaces() {
        assertThatThrownBy(() -> new IntegrationTestDatasourceGuard().createContextCustomizer(
                InterfaceDynamicDatasourceTest.class, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@DynamicPropertySource");
    }

    @Test
    void rejectsNonEnumerablePropertySources() {
        MockEnvironment environment = environment(testProperties());
        environment.getPropertySources().addFirst(new PropertySource<Object>("concealed-override") {
            @Override
            public Object getProperty(String name) {
                return "spring.datasource.hikari.jdbc-url".equals(name)
                        ? "jdbc:postgresql://localhost:5432/shared"
                        : null;
            }
        });

        assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateH2Environment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-enumerable property source");
    }

    @Test
    void acceptsEffectiveInMemoryHikariConfiguration() {
        com.zaxxer.hikari.HikariDataSource datasource = new com.zaxxer.hikari.HikariDataSource();
        datasource.setJdbcUrl("jdbc:h2:mem:effective-check;MODE=PostgreSQL");
        datasource.setDriverClassName("org.h2.Driver");
        datasource.setUsername("sa");

        assertThatCode(() -> IntegrationTestDatasourceGuard.validateHikariDatasource(datasource))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExternalHikariConfigurationAfterConnectionDetailsProcessing() {
        com.zaxxer.hikari.HikariDataSource datasource = new com.zaxxer.hikari.HikariDataSource();
        datasource.setJdbcUrl("jdbc:postgresql://localhost:5432/shared");
        datasource.setDriverClassName("org.postgresql.Driver");
        datasource.setUsername("shared");

        assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateHikariDatasource(datasource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("effective Hikari datasource");
    }

    @Test
    void rejectsFileBackedAndNonH2JdbcUrls() {
        for (String jdbcUrl : new String[]{
                "jdbc:h2:file:C:/shared/testdb",
                "jdbc:h2:mem:external;AUTO_SERVER=TRUE",
                "jdbc:mysql://localhost:3306/shared",
                "jdbc:h2:mem:init;INIT=RUNSCRIPT FROM 'file.sql'"
        }) {
            com.zaxxer.hikari.HikariDataSource datasource = new com.zaxxer.hikari.HikariDataSource();
            datasource.setJdbcUrl(jdbcUrl);
            datasource.setUsername("sa");
            assertThatThrownBy(() ->
                    IntegrationTestDatasourceGuard.validateHikariDatasource(datasource))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsFlywayAndDirectHibernateDatasourceOverrides() {
        for (String property : new String[]{
                "spring.flyway.user",
                "hibernate.connection.url",
                "spring.jpa.properties.hibernate.connection.url",
                "jakarta.persistence.jdbc.url"
        }) {
            Map<String, Object> properties = testProperties();
            properties.put(property, "jdbc:postgresql://localhost:5432/shared");
            assertThatThrownBy(() -> IntegrationTestDatasourceGuard.validateH2Environment(
                    environment(properties)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsConcurrencyDatasourceUnlessExplicitDedicatedTargetIsApproved() {
        assertThatThrownBy(() -> PostgresConcurrencyDatasourceGuard.validateConfiguration(
                "true", null, PostgresConcurrencyDatasourceGuard.APPROVED_URL,
                PostgresConcurrencyDatasourceGuard.APPROVED_TARGET,
                "test-user", "test-password", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsOnlyTheExplicitlyApprovedDedicatedConcurrencyTarget() {
        assertThatCode(() -> PostgresConcurrencyDatasourceGuard.validateConfiguration(
                "true", "true", PostgresConcurrencyDatasourceGuard.APPROVED_URL,
                PostgresConcurrencyDatasourceGuard.APPROVED_TARGET,
                "test-user", "test-password", Map.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> PostgresConcurrencyDatasourceGuard.validateConfiguration(
                "true", "true", PostgresConcurrencyDatasourceGuard.APPROVED_URL,
                PostgresConcurrencyDatasourceGuard.APPROVED_TARGET,
                "test-user", "test-password",
                Map.of("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/dev")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, Object> testProperties() {
        return new java.util.HashMap<>(Map.of(
                "spring.datasource.url",
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "spring.datasource.username", "sa",
                "spring.datasource.password", "",
                "spring.datasource.driver-class-name", "org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto", "create-drop",
                "spring.flyway.enabled", "false"));
    }

    private static MockEnvironment environment(Map<String, Object> properties) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test-configuration", properties));
        return environment;
    }

    private static class UnapprovedBeanPostProcessor implements BeanPostProcessor {}

    private static class CustomBeanPostProcessor implements BeanPostProcessor {}

    private static ApplicationContextRunner bootManagedH2Runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("test");
                    IntegrationTestDatasourceGuard.installGuardForTest(
                            context, BootManagedH2Test.class);
                })
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "spring.datasource.url=jdbc:h2:mem:guard-context;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.flyway.enabled=false");
    }

    private static ApplicationContextRunner dataJpaH2Runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TestDatabaseAutoConfiguration.class,
                        DataSourceAutoConfiguration.class))
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("test");
                    IntegrationTestDatasourceGuard.installGuardForTest(
                            context, EmbeddedDataJpaTest.class);
                })
                .withPropertyValues(
                        "spring.profiles.active=test",
                        "spring.test.database.replace=ANY",
                        "spring.datasource.url=jdbc:h2:mem:guard-slice;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.flyway.enabled=false");
    }

    @SpringBootTest
    @ActiveProfiles("test")
    private static class BootManagedH2Test {}

    @DataJpaTest
    @ActiveProfiles("test")
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.INHERIT)
    private static class EmbeddedDataJpaTest {}

    @DataJpaTest
    @ActiveProfiles("test")
    @AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.HSQLDB)
    private static class NonH2EmbeddedDataJpaTest {}

    @Configuration(proxyBeanMethods = false)
    static class ExternalJdbcConnectionDetailsConfiguration {
        @Bean
        JdbcConnectionDetails externalJdbcConnectionDetails() {
            return new JdbcConnectionDetails() {
                @Override
                public String getUsername() {
                    return "external-user";
                }

                @Override
                public String getPassword() {
                    return "external-password";
                }

                @Override
                public String getJdbcUrl() {
                    return "jdbc:postgresql://localhost:5432/shared";
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDatasourceConfiguration {
        @Bean
        DataSource dataSource() {
            com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:custom");
            dataSource.setUsername("sa");
            return dataSource;
        }
    }

    private interface DynamicPropertySourceInterface {
        @DynamicPropertySource
        static void overrideDatasource(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost/shared");
        }
    }

    @SpringBootTest
    private static class InterfaceDynamicDatasourceTest implements DynamicPropertySourceInterface {}

    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.INHERIT)
    private static class DynamicPropertyEnclosingTest {
        @DynamicPropertySource
        static void overrideDatasource(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost/shared");
        }

        @Nested
        @SpringBootTest
        private class EnclosedDynamicDatasourceTest {}
    }

    @SpringBootTest
    private static class DynamicDatasourceTest {
        @DynamicPropertySource
        static void overrideDatasource(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url",
                    () -> "jdbc:postgresql://localhost:5432/shared");
        }
    }

    private abstract static class DynamicPropertyBase {
        @DynamicPropertySource
        static void overrideDatasource(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url",
                    () -> "jdbc:postgresql://localhost:5432/shared");
        }
    }

    @SpringBootTest
    private static class InheritedDynamicDatasourceTest extends DynamicPropertyBase {}
}

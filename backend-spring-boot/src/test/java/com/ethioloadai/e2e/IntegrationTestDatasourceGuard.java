package com.ethioloadai.e2e;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

import javax.sql.DataSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class IntegrationTestDatasourceGuard implements ContextCustomizerFactory {

    private static final String GUARD_REGISTRY_PROCESSOR =
            IntegrationTestDatasourceGuard.class.getName() + ".registryProcessor";
    private static final String GUARD_DATASOURCE_PROCESSOR =
            IntegrationTestDatasourceGuard.class.getName() + ".dataSourceProcessor";
    private static final String POSTGRES_CONCURRENCY_TEST =
            "com.ethioloadai.carrier.dispatch.FreightCancellationPostgresConcurrencyTest";
    private static final String BOOT_HIKARI_CONFIGURATION =
            "org.springframework.boot.autoconfigure.jdbc.DataSourceConfiguration$Hikari";
    private static final String BOOT_PROPERTIES_CONNECTION_DETAILS =
            "org.springframework.boot.autoconfigure.jdbc.PropertiesJdbcConnectionDetails";
    private static final String BOOT_TEST_DATABASE_FACTORY =
            "org.springframework.boot.test.autoconfigure.jdbc.TestDatabaseAutoConfiguration$EmbeddedDataSourceFactoryBean";
    private static final String BOOT_PROPERTY_MAPPING_PROCESSOR =
            "org.springframework.boot.test.autoconfigure.properties.PropertyMappingContextCustomizer"
                    + "$PropertyMappingCheckBeanPostProcessor";
    private static final String BOOT_CONFIGURATION_PROPERTY_SOURCE =
            "org.springframework.boot.context.properties.source.ConfigurationPropertySourcesPropertySource";
    private static final String BOOT_RANDOM_PROPERTY_SOURCE =
            "org.springframework.boot.env.RandomValuePropertySource";
    private static final String SPRING_STUB_PROPERTY_SOURCE =
            "org.springframework.core.env.PropertySource$StubPropertySource";
    private static final Set<String> ALLOWED_DATASOURCE_PROPERTIES = Set.of(
            normalize("spring.datasource.url"),
            normalize("spring.datasource.username"),
            normalize("spring.datasource.password"),
            normalize("spring.datasource.driver-class-name"));
    private static final Set<String> ALLOWED_FLYWAY_PROPERTIES = Set.of(
            normalize("spring.flyway.enabled"));
    private static final Set<String> ALLOWED_CUSTOM_FLYWAY_PROPERTIES = Set.of(
            normalize("flyway.custom.enabled"),
            normalize("flyway.repair"));
    private static final Set<String> ALLOWED_H2_OPTIONS = Set.of(
            "db_close_delay",
            "db_close_on_exit",
            "mode",
            "non_keywords");

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {
        if (!isSpringBootTest(testClass)) {
            return null;
        }
        if (!isPostgresConcurrencyTest(testClass) && hasDynamicPropertySourceMethod(testClass)) {
            throw unsafe("ordinary H2 integration tests must not declare @DynamicPropertySource methods");
        }
        return new GuardContextCustomizer(testClass);
    }

    private static boolean isSpringBootTest(Class<?> testClass) {
        return AnnotatedElementUtils.hasAnnotation(testClass, SpringBootTest.class)
                || AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class);
    }

    private static boolean isPostgresConcurrencyTest(Class<?> testClass) {
        return POSTGRES_CONCURRENCY_TEST.equals(testClass.getName());
    }

    static boolean hasDynamicPropertySourceMethod(Class<?> testClass) {
        Set<Class<?>> searched = new HashSet<>();
        for (Class<?> candidate = testClass; candidate != null && candidate != Object.class;
                candidate = candidate.getSuperclass()) {
            if (hasDynamicPropertyInHierarchy(candidate, searched)) {
                return true;
            }
            if (TestContextAnnotationUtils.searchEnclosingClass(candidate)) {
                for (Class<?> enclosing = candidate.getEnclosingClass();
                        enclosing != null;
                        enclosing = enclosing.getEnclosingClass()) {
                    if (hasDynamicPropertyInHierarchy(enclosing, searched)) {
                        return true;
                    }
                    if (!TestContextAnnotationUtils.searchEnclosingClass(enclosing)) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasDynamicPropertyInHierarchy(Class<?> candidate, Set<Class<?>> searched) {
        if (!searched.add(candidate)) {
            return false;
        }
        for (Method method : candidate.getDeclaredMethods()) {
            if (MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY)
                    .isPresent(DynamicPropertySource.class)) {
                return true;
            }
        }
        for (Class<?> interfaceType : candidate.getInterfaces()) {
            if (hasDynamicPropertyInHierarchy(interfaceType, searched)) {
                return true;
            }
        }
        return false;
    }

    private static void validateContext(
            Class<?> testClass,
            ConfigurableEnvironment environment,
            ConfigurableListableBeanFactory beanFactory) {
        if (isPostgresConcurrencyTest(testClass)) {
            PostgresConcurrencyDatasourceGuard.validate();
            return;
        }
        validateH2Environment(environment);
        validateDataJpaTestDatabaseAnnotation(testClass);
        if (AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class)
                && !"H2".equalsIgnoreCase(
                        environment.getProperty("spring.test.database.connection"))) {
            throw unsafe("@DataJpaTest must explicitly use Boot's H2 embedded database");
        }
        validateProcessorDefinitions(beanFactory);
        validateJdbcConnectionDetails(beanFactory);
        validateNoFlywayBeans(beanFactory);
        validateDatasourceDefinition(beanFactory, testClass);
        registerDatasourceProcessor(beanFactory, testClass, environment);
    }

    static void validateH2Environment(ConfigurableEnvironment environment) {
        validatePropertySourceSafety(environment);
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1 || !"test".equals(activeProfiles[0])) {
            throw unsafe("Spring integration tests must activate only the test profile");
        }

        requireEveryValue(environment, "spring.datasource.url",
                IntegrationTestDatasourceGuard::isInMemoryH2Url);
        requireEffectiveValue(environment, "spring.datasource.url",
                IntegrationTestDatasourceGuard::isInMemoryH2Url);
        requireEveryValue(environment, "spring.datasource.driver-class-name",
                value -> value == null || "org.h2.Driver".equals(value));
        requireEffectiveValue(environment, "spring.datasource.driver-class-name",
                value -> value == null || "org.h2.Driver".equals(value));
        requireEveryValue(environment, "spring.datasource.username", "sa"::equals);
        requireEffectiveValue(environment, "spring.datasource.username", "sa"::equals);
        requireEveryValue(environment, "spring.datasource.password",
                value -> value == null || value.isBlank());
        requireEffectiveValue(environment, "spring.datasource.password",
                value -> value == null || value.isBlank());
        requireEffectiveValue(environment, "spring.jpa.hibernate.ddl-auto",
                "create-drop"::equals);
        requireEveryValue(environment, "spring.flyway.enabled",
                value -> "false".equalsIgnoreCase(value));
        requireEffectiveValue(environment, "spring.flyway.enabled",
                value -> "false".equalsIgnoreCase(value));
        requireEffectiveValue(environment, "flyway.custom.enabled",
                value -> value == null || "false".equalsIgnoreCase(value));
        requireNoUnexpectedDatasourceOrFlywayProperties(environment);
    }

    static void validateJdbcConnectionDetails(ConfigurableListableBeanFactory beanFactory) {
        String[] names = beanFactory.getBeanNamesForType(JdbcConnectionDetails.class, true, false);
        for (String name : names) {
            if (!beanFactory.containsBeanDefinition(name)
                    || beanFactory.getType(name, false) == null
                    || !BOOT_PROPERTIES_CONNECTION_DETAILS.equals(
                            beanFactory.getType(name, false).getName())) {
                throw unsafe("custom JdbcConnectionDetails beans are not allowed in ordinary H2 tests");
            }
        }
    }

    static void validateDataJpaTestDatabaseAnnotation(Class<?> testClass) {
        if (!AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class)) {
            return;
        }
        AutoConfigureTestDatabase databaseConfiguration =
                AnnotatedElementUtils.findMergedAnnotation(
                        testClass, AutoConfigureTestDatabase.class);
        if (databaseConfiguration != null
                && databaseConfiguration.connection() != EmbeddedDatabaseConnection.NONE
                && databaseConfiguration.connection() != EmbeddedDatabaseConnection.H2) {
            throw unsafe("@DataJpaTest cannot select a non-H2 embedded database");
        }
    }

    private static void validateNoFlywayBeans(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory.getBeanNamesForType(Flyway.class, true, false).length > 0) {
            throw unsafe("Flyway beans are not allowed in ordinary H2 integration tests");
        }
    }

    private static void validateDatasourceDefinition(
            ConfigurableListableBeanFactory beanFactory, Class<?> testClass) {
        String[] names = beanFactory.getBeanNamesForType(DataSource.class, true, false);
        if (names.length != 1) {
            throw unsafe("ordinary H2 tests require exactly one identifiable DataSource definition");
        }
        String name = names[0];
        if (!"dataSource".equals(name) || !beanFactory.containsBeanDefinition(name)) {
            throw unsafe("custom or aliased DataSource definitions are not allowed");
        }
        if (beanFactory.getAliases(name).length > 0) {
            throw unsafe("DataSource aliases are not allowed in ordinary H2 tests");
        }

        BeanDefinition definition = beanFactory.getBeanDefinition(name);
        String factoryBeanName = definition.getFactoryBeanName();
        String factoryBeanClass = factoryBeanName != null
                && beanFactory.containsBeanDefinition(factoryBeanName)
                ? beanFactory.getBeanDefinition(factoryBeanName).getBeanClassName()
                : null;
        String definitionClass = definition.getBeanClassName();

        boolean bootHikari = BOOT_HIKARI_CONFIGURATION.equals(factoryBeanClass)
                && "dataSource".equals(definition.getFactoryMethodName())
                && HikariDataSource.class.equals(beanFactory.getType(name, false));
        boolean bootEmbeddedSlice = AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class)
                && BOOT_TEST_DATABASE_FACTORY.equals(definitionClass);
        if (!bootHikari && !bootEmbeddedSlice) {
            throw unsafe("DataSource definition is not a supported Boot H2 configuration");
        }
    }

    static void validateProcessorDefinitions(ConfigurableListableBeanFactory beanFactory) {
        for (String name : beanFactory.getBeanNamesForType(
                BeanDefinitionRegistryPostProcessor.class, true, false)) {
            requireFrameworkProcessor(beanFactory, name, "registry post-processor");
        }
        for (String name : beanFactory.getBeanNamesForType(
                org.springframework.beans.factory.config.BeanFactoryPostProcessor.class, true, false)) {
            requireFrameworkProcessor(beanFactory, name, "factory post-processor");
        }
        for (String name : beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false)) {
            requireFrameworkProcessor(beanFactory, name, "bean post-processor");
        }
    }

    static void requireFrameworkProcessor(
            ConfigurableListableBeanFactory beanFactory, String name, String category) {
        if (GUARD_REGISTRY_PROCESSOR.equals(name) || GUARD_DATASOURCE_PROCESSOR.equals(name)) {
            return;
        }
        Class<?> type = resolveProcessorType(beanFactory, name);
        if (!beanFactory.containsBeanDefinition(name)) {
            if ("bean post-processor".equals(category)
                    && BOOT_PROPERTY_MAPPING_PROCESSOR.equals(name)
                    && type != null
                    && BOOT_PROPERTY_MAPPING_PROCESSOR.equals(type.getName())) {
                return;
            }
            String typeName = type == null ? "unresolved" : type.getName();
            throw unsafe("programmatically registered Spring " + category
                    + " is not allowed: bean '" + name + "' (type " + typeName + ")");
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(name);
        String factoryBeanName = definition.getFactoryBeanName();
        Class<?> factoryType = factoryBeanName == null
                ? null
                : beanFactory.getType(factoryBeanName, false);
        boolean frameworkDefinition = factoryBeanName == null
                ? definition.getBeanClassName() != null
                        && isFrameworkClassName(definition.getBeanClassName())
                : factoryType != null && isFrameworkType(factoryType);
        if (!frameworkDefinition || type == null || !isFrameworkType(type)) {
            throw unsafe("custom Spring post-processors are not allowed in ordinary H2 tests");
        }
    }

    private static Class<?> resolveProcessorType(
            ConfigurableListableBeanFactory beanFactory, String name) {
        try {
            return beanFactory.getType(name, false);
        } catch (BeansException exception) {
            return null;
        }
    }

    private static boolean isFrameworkType(Class<?> type) {
        return isFrameworkClassName(type.getName());
    }

    private static boolean isFrameworkClassName(String className) {
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd < 0) {
            return false;
        }
        String packageName = className.substring(0, packageEnd);
        return packageName.startsWith("org.springframework.")
                || packageName.startsWith("org.springframework.boot.");
    }

    private static void validatePropertySourceSafety(ConfigurableEnvironment environment) {
        validatePropertySources(environment.getPropertySources());
    }

    private static void validatePropertySources(Iterable<PropertySource<?>> propertySources) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof CompositePropertySource composite) {
                validatePropertySources(composite.getPropertySources());
                continue;
            }
            if (propertySource instanceof EnumerablePropertySource<?>) {
                continue;
            }
            String sourceClass = propertySource.getClass().getName();
            String sourceName = propertySource.getName();
            boolean knownSafeSource =
                    BOOT_CONFIGURATION_PROPERTY_SOURCE.equals(sourceClass)
                            && "configurationProperties".equals(sourceName)
                    || BOOT_RANDOM_PROPERTY_SOURCE.equals(sourceClass)
                            && "random".equals(sourceName)
                    || SPRING_STUB_PROPERTY_SOURCE.equals(sourceClass)
                            && ("servletContextInitParams".equals(sourceName)
                                    || "servletConfigInitParams".equals(sourceName));
            if (!knownSafeSource) {
                throw unsafe("non-enumerable property source cannot be proven safe: " + sourceName);
            }
        }
    }

    private static void requireEveryValue(
            ConfigurableEnvironment environment,
            String property,
            java.util.function.Predicate<String> allowed) {
        List<Object> values = propertyValues(environment, property);
        if (values.isEmpty()) {
            throw unsafe("required test datasource property is missing: " + property);
        }
        for (Object value : values) {
            String resolved = value == null
                    ? null
                    : environment.resolvePlaceholders(String.valueOf(value));
            if (!allowed.test(resolved)) {
                throw unsafe("conflicting test datasource property: " + property);
            }
        }
    }

    private static void requireEffectiveValue(
            ConfigurableEnvironment environment,
            String property,
            java.util.function.Predicate<String> allowed) {
        String value = environment.getProperty(property);
        if (!allowed.test(value)) {
            throw unsafe("unsafe effective test datasource property: " + property);
        }
    }

    private static void requireNoUnexpectedDatasourceOrFlywayProperties(
            ConfigurableEnvironment environment) {
        for (String name : propertyNames(environment)) {
            String normalized = normalize(name);
            if (normalized.startsWith(normalize("spring.datasource."))
                    && !ALLOWED_DATASOURCE_PROPERTIES.contains(normalized)) {
                throw unsafe("alternate datasource configuration is not allowed");
            }
            if (normalized.startsWith(normalize("spring.flyway."))
                    && !ALLOWED_FLYWAY_PROPERTIES.contains(normalized)) {
                throw unsafe("alternate Flyway configuration is not allowed");
            }
            if (normalized.startsWith(normalize("flyway."))
                    && !ALLOWED_CUSTOM_FLYWAY_PROPERTIES.contains(normalized)) {
                throw unsafe("alternate custom Flyway configuration is not allowed");
            }
            if (isDirectHibernateDatasourceProperty(normalized)) {
                throw unsafe("direct Hibernate datasource configuration is not allowed");
            }
        }
    }

    private static boolean isDirectHibernateDatasourceProperty(String normalized) {
        boolean springJpaDatasourceProperty =
                normalized.startsWith(normalize("spring.jpa.properties."))
                        && (normalized.contains("connection")
                                || normalized.contains("datasource")
                                || normalized.contains("jdbc")
                                || normalized.contains("hikari")
                                || normalized.contains("c3p0")
                                || normalized.contains("proxool"));
        return springJpaDatasourceProperty
                || normalized.equals(normalize("spring.jpa.jndi-name"))
                || normalized.equals(normalize("spring.jpa.datasource"))
                || normalized.startsWith(normalize("hibernate.connection."))
                || normalized.startsWith(normalize("hibernate.hikari."))
                || normalized.startsWith(normalize("hibernate.c3p0."))
                || normalized.startsWith(normalize("hibernate.proxool."))
                || normalized.startsWith(normalize("hibernate.datasource."))
                || normalized.startsWith(normalize("jakarta.persistence.jdbc."))
                || normalized.startsWith(normalize("javax.persistence.jdbc."))
                || normalized.equals(normalize("jakarta.persistence.non-jta-data-source"))
                || normalized.equals(normalize("jakarta.persistence.jta-data-source"))
                || normalized.equals(normalize("javax.persistence.non-jta-data-source"))
                || normalized.equals(normalize("javax.persistence.jta-data-source"));
    }

    private static boolean isInMemoryH2Url(String value) {
        if (value == null || !value.matches("(?i)^jdbc:h2:mem:[^;]+(?:;[^;]*)*$")) {
            return false;
        }
        String[] parts = value.split(";");
        for (int i = 1; i < parts.length; i++) {
            String[] option = parts[i].split("=", 2);
            if (option.length != 2 || !ALLOWED_H2_OPTIONS.contains(option[0].trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
            String optionValue = option[1].trim();
            switch (option[0].trim().toLowerCase(Locale.ROOT)) {
                case "db_close_delay" -> {
                    if (!optionValue.matches("-?\\d+")) {
                        return false;
                    }
                }
                case "db_close_on_exit" -> {
                    if (!"true".equalsIgnoreCase(optionValue) && !"false".equalsIgnoreCase(optionValue)) {
                        return false;
                    }
                }
                case "mode" -> {
                    if (!"postgresql".equalsIgnoreCase(optionValue)) {
                        return false;
                    }
                }
                case "non_keywords" -> {
                    if (!optionValue.matches("(?i)[a-z0-9_,]+")) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Object> propertyValues(
            ConfigurableEnvironment environment, String property) {
        List<Object> values = new ArrayList<>();
        collectPropertyValues(
                environment.getPropertySources(), normalize(property), property, values);
        return values;
    }

    private static void collectPropertyValues(
            Iterable<PropertySource<?>> propertySources,
            String normalizedProperty,
            String property,
            List<Object> values) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof CompositePropertySource composite) {
                collectPropertyValues(
                        composite.getPropertySources(), normalizedProperty, property, values);
            } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                boolean found = false;
                for (String name : enumerable.getPropertyNames()) {
                    if (normalize(name).equals(normalizedProperty)) {
                        values.add(enumerable.getProperty(name));
                        found = true;
                    }
                }
                if (!found && propertySource.containsProperty(property)) {
                    values.add(propertySource.getProperty(property));
                }
            } else if (propertySource.containsProperty(property)) {
                values.add(propertySource.getProperty(property));
            }
        }
    }

    private static List<String> propertyNames(ConfigurableEnvironment environment) {
        List<String> names = new ArrayList<>();
        collectPropertyNames(environment.getPropertySources(), names);
        return names;
    }

    private static void collectPropertyNames(
            Iterable<PropertySource<?>> propertySources, List<String> names) {
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof CompositePropertySource composite) {
                collectPropertyNames(composite.getPropertySources(), names);
            } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                names.addAll(List.of(enumerable.getPropertyNames()));
            }
        }
    }

    private static String normalize(String propertyName) {
        return propertyName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static IllegalStateException unsafe(String reason) {
        return new IllegalStateException("Unsafe Spring integration-test datasource: " + reason);
    }

    static void installGuardForTest(
            ConfigurableApplicationContext context, Class<?> testClass) {
        installGuard(context, testClass, AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class));
    }

    private static void registerGuard(
            ConfigurableApplicationContext context,
            Class<?> testClass,
            MergedContextConfiguration mergedConfig) {
        if (isPostgresConcurrencyTest(testClass)) {
            return;
        }
        rejectUnsupportedCustomizers(mergedConfig);
        rejectUnsupportedInitializers(mergedConfig);
        installGuard(context, testClass, AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class));
    }

    private static void installGuard(
            ConfigurableApplicationContext context, Class<?> testClass, boolean dataJpaTest) {
        if (!(context.getBeanFactory() instanceof BeanDefinitionRegistry registry)) {
            throw unsafe("test context does not expose a bean-definition registry");
        }
        if (registry.containsBeanDefinition(GUARD_REGISTRY_PROCESSOR)) {
            throw unsafe("datasource guard processor bean name is already in use");
        }
        if (dataJpaTest) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "ordinaryH2IntegrationTest",
                    Map.of("spring.test.database.connection", "H2")));
        }
        RootBeanDefinition guardDefinition =
                new RootBeanDefinition(GuardRegistryPostProcessor.class);
        guardDefinition.setInstanceSupplier(() -> new GuardRegistryPostProcessor(
                testClass, context.getEnvironment()));
        guardDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(GUARD_REGISTRY_PROCESSOR, guardDefinition);
    }

    private static void rejectUnsupportedCustomizers(MergedContextConfiguration mergedConfig) {
        for (ContextCustomizer customizer : mergedConfig.getContextCustomizers()) {
            if (customizer instanceof GuardContextCustomizer) {
                continue;
            }
            if (!isFrameworkType(customizer.getClass())) {
                throw unsafe("custom test context customizers are not allowed for ordinary H2 tests");
            }
        }
    }

    private static void rejectUnsupportedInitializers(MergedContextConfiguration mergedConfig) {
        for (Class<?> initializer : mergedConfig.getContextInitializerClasses()) {
            if (!isFrameworkType(initializer)) {
                throw unsafe("custom test context initializers are not allowed for ordinary H2 tests");
            }
        }
    }

    private record GuardContextCustomizer(Class<?> testClass) implements ContextCustomizer {
        @Override
        public void customizeContext(
                ConfigurableApplicationContext context,
                MergedContextConfiguration mergedConfig) {
            registerGuard(context, testClass, mergedConfig);
        }
    }

    private static final class GuardRegistryPostProcessor
            implements BeanDefinitionRegistryPostProcessor, Ordered {

        private final Class<?> testClass;
        private final ConfigurableEnvironment environment;

        private GuardRegistryPostProcessor(
                Class<?> testClass, ConfigurableEnvironment environment) {
            this.testClass = testClass;
            this.environment = environment;
        }

        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE;
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {}

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            validateContext(testClass, environment, beanFactory);
        }
    }

    private static final class GuardDataSourceBeanPostProcessor
            implements BeanPostProcessor, PriorityOrdered {

        private final Class<?> testClass;
        private final ConfigurableEnvironment environment;

        private GuardDataSourceBeanPostProcessor(
                Class<?> testClass, ConfigurableEnvironment environment) {
            this.testClass = testClass;
            this.environment = environment;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 3;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            validateDatasourceInstance(beanName, bean, testClass, environment);
            return bean;
        }
    }

    static void validateDatasourceInstance(
            String beanName,
            Object bean,
            Class<?> testClass,
            ConfigurableEnvironment environment) {
        if (!"dataSource".equals(beanName) || !(bean instanceof DataSource dataSource)) {
            return;
        }
        if (dataSource instanceof HikariDataSource hikari) {
            validateHikariDatasource(hikari);
        } else if (dataSource instanceof EmbeddedDatabase) {
            validateDataJpaTestDatabaseAnnotation(testClass);
            validateEmbeddedDatasource(testClass, environment);
        } else {
            throw unsafe("final DataSource instance is not a supported in-memory H2 datasource");
        }
    }

    static void validateHikariDatasource(HikariDataSource dataSource) {
        if (!isInMemoryH2Url(dataSource.getJdbcUrl())
                || (dataSource.getDriverClassName() != null
                        && !"org.h2.Driver".equals(dataSource.getDriverClassName()))
                || !"sa".equals(dataSource.getUsername())
                || (dataSource.getPassword() != null && !dataSource.getPassword().isBlank())
                || dataSource.getDataSource() != null
                || dataSource.getDataSourceClassName() != null) {
            throw unsafe("effective Hikari datasource configuration is not in-memory H2");
        }
    }

    private static void validateEmbeddedDatasource(
            Class<?> testClass, ConfigurableEnvironment environment) {
        if (!AnnotatedElementUtils.hasAnnotation(testClass, DataJpaTest.class)
                || !"H2".equalsIgnoreCase(environment.getProperty("spring.test.database.connection"))
                || !isInMemoryH2Url(environment.getProperty("spring.datasource.url"))) {
            throw unsafe("embedded test datasource is not explicitly configured as H2");
        }
    }

    private static void registerDatasourceProcessor(
            ConfigurableListableBeanFactory beanFactory,
            Class<?> testClass,
            ConfigurableEnvironment environment) {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            throw unsafe("test bean factory cannot register datasource validation");
        }
        if (registry.containsBeanDefinition(GUARD_DATASOURCE_PROCESSOR)) {
            throw unsafe("datasource validation processor bean name is already in use");
        }
        RootBeanDefinition definition = new RootBeanDefinition(GuardDataSourceBeanPostProcessor.class);
        definition.setInstanceSupplier(() ->
                new GuardDataSourceBeanPostProcessor(testClass, environment));
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(GUARD_DATASOURCE_PROCESSOR, definition);
    }
}

package com.ethioloadai.vehicle.document.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleDocumentControllerMappingTest {

    @Test
    void vehicleDocumentRoutesResolveToExpectedExternalPaths() throws IOException {
        String contextPath = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .get(0)
            .getProperty("server.servlet.context-path")
            .toString();
        Map<String, String> externalPaths = Arrays.stream(VehicleDocumentController.class.getDeclaredMethods())
            .collect(Collectors.toMap(
                Method::getName,
                method -> contextPath + mappingPath(method)
            ));

        assertThat(externalPaths).containsEntry(
            "listVehicleDocuments", "/api/vehicles/{vehicleId}/documents");
        assertThat(externalPaths).containsEntry(
            "uploadVehicleDocument", "/api/vehicles/{vehicleId}/documents");
        assertThat(externalPaths).containsEntry(
            "getVehicleDocumentFile", "/api/vehicle-documents/{documentId}/file");
        assertThat(externalPaths).containsEntry(
            "listAllVehicleDocuments", "/api/admin/vehicle-documents");
        assertThat(externalPaths).containsEntry(
            "reviewVehicleDocument", "/api/admin/vehicle-documents/{documentId}/review");
    }

    private String mappingPath(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return method.getAnnotation(GetMapping.class).value()[0];
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return method.getAnnotation(PostMapping.class).value()[0];
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return method.getAnnotation(PatchMapping.class).value()[0];
        }
        throw new AssertionError("No supported request mapping on " + method.getName());
    }
}

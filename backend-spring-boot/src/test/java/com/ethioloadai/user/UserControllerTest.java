package com.ethioloadai.user;

import com.ethioloadai.user.controller.UserController;
import com.ethioloadai.user.dto.*;
import com.ethioloadai.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("GET /api/users returns 200 OK with UserListResponse")
    void listUsers_returns200Ok() {
        UserListResponse mockList = UserListResponse.builder()
                .users(List.of(UserResponse.builder().id(1L).name("Test User").role("shipper").build()))
                .total(1L)
                .build();

        when(userService.listUsers(any(), anyInt(), anyInt())).thenReturn(mockList);

        ResponseEntity<UserListResponse> response = userController.listUsers("shipper", 0, 50, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUsers()).hasSize(1);
        assertThat(response.getBody().getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("POST /api/admin/users returns 201 Created with user envelope")
    void createUser_returns201Created() {
        CreateUserRequest request = CreateUserRequest.builder()
                .name("New User")
                .email("new@example.com")
                .phone("+251911111111")
                .password("pwd123")
                .role("shipper")
                .build();

        UserResponse mockResponse = UserResponse.builder().id(2L).name("New User").build();
        when(userService.createUser(request)).thenReturn(mockResponse);

        ResponseEntity<Map<String, Object>> response = userController.createUser(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("user");
        assertThat(response.getBody().get("user")).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("PUT /api/admin/users/{id} returns 200 OK with updated user")
    void updateUser_returns200Ok() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .name("Updated Name")
                .build();

        UserResponse mockResponse = UserResponse.builder().id(2L).name("Updated Name").build();
        when(userService.updateUser(eq(2L), eq(request))).thenReturn(mockResponse);

        ResponseEntity<Map<String, Object>> response = userController.updateUser(2L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("user");
    }

    @Test
    @DisplayName("DELETE /api/admin/users/{id} returns 200 OK")
    void deleteUser_returns200Ok() {
        doNothing().when(userService).deleteUser(2L);

        ResponseEntity<Map<String, String>> response = userController.deleteUser(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "User deleted successfully");
        verify(userService).deleteUser(2L);
    }

    @Test
    @DisplayName("POST /api/admin/drivers returns 201 Created")
    void createDriver_returns201Created() {
        CreateDriverAdminRequest request = CreateDriverAdminRequest.builder()
                .name("Driver Dave")
                .email("dave@example.com")
                .phone("+251911111111")
                .password("pwd123")
                .build();

        when(userService.createDriver(request)).thenReturn(Map.of("user", Map.of("id", 3L), "driver", Map.of("id", 3L)));

        ResponseEntity<Map<String, Object>> response = userController.createDriver(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("user");
        assertThat(response.getBody()).containsKey("driver");
    }

    @Test
    @DisplayName("UserController has class-level @PreAuthorize(\"hasRole('ADMIN')\")")
    void controllerHasAdminPreAuthorize() {
        PreAuthorize annotation = UserController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }
}

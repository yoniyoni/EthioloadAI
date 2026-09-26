package com.ethioloadai.user;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.dto.*;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.exception.UserException;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.user.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FreightRepository freightRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(10L)
                .fullName("Abebe Bikila")
                .email("abebe@example.com")
                .phone("+251911111111")
                .password("encoded_pwd")
                .role(User.Role.SHIPPER)
                .verificationStatus(true)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsersTests {

        @Test
        @DisplayName("Returns paginated list of all users")
        void returnsAllUsersWhenNoRoleFilter() {
            when(userRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(sampleUser)));

            UserListResponse response = userService.listUsers(null, 0, 50);

            assertThat(response).isNotNull();
            assertThat(response.getUsers()).hasSize(1);
            assertThat(response.getUsers().get(0).getName()).isEqualTo("Abebe Bikila");
            assertThat(response.getTotal()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Filters users by role when role provided")
        void filtersUsersByRole() {
            when(userRepository.findByRole(eq(User.Role.DRIVER), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            UserListResponse response = userService.listUsers("driver", 0, 50);

            assertThat(response.getUsers()).isEmpty();
            assertThat(response.getTotal()).isEqualTo(0L);
            verify(userRepository).findByRole(eq(User.Role.DRIVER), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUserTests {

        @Test
        @DisplayName("Successfully creates new user with encoded password")
        void createsUserSuccessfully() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Kebede Michael")
                    .email("kebede@example.com")
                    .phone("+251922222222")
                    .password("secret123")
                    .role("driver")
                    .build();

            when(userRepository.existsByEmail("kebede@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("+251922222222")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(15L);
                return u;
            });

            UserResponse response = userService.createUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(15L);
            assertThat(response.getName()).isEqualTo("Kebede Michael");
            assertThat(response.getRole()).isEqualTo("driver");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Throws UserAlreadyExistsException when email already registered")
        void throwsWhenEmailExists() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Kebede")
                    .email("exists@example.com")
                    .phone("+251922222222")
                    .password("secret123")
                    .role("driver")
                    .build();

            when(userRepository.existsByEmail("exists@example.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(UserException.UserAlreadyExistsException.class)
                    .hasMessageContaining("Email already registered");
        }

        @Test
        @DisplayName("Throws UserValidationException when role is invalid")
        void throwsWhenRoleInvalid() {
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Kebede")
                    .email("new@example.com")
                    .phone("+251922222222")
                    .password("secret123")
                    .role("superhero")
                    .build();

            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("+251922222222")).thenReturn(false);

            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(UserException.UserValidationException.class)
                    .hasMessageContaining("Invalid role");
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUserTests {

        @Test
        @DisplayName("Updates user fields and re-hashes password if provided")
        void updatesUserFields() {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("Abebe Updated")
                    .email("abebe_new@example.com")
                    .phone("+251911111111")
                    .role("fleet_owner")
                    .password("newsecret456")
                    .isActive(false)
                    .build();

            when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
            when(userRepository.existsByEmailAndIdNot("abebe_new@example.com", 10L)).thenReturn(false);
            when(passwordEncoder.encode("newsecret456")).thenReturn("hashed_new_pwd");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse response = userService.updateUser(10L, request);

            assertThat(response.getName()).isEqualTo("Abebe Updated");
            assertThat(response.getEmail()).isEqualTo("abebe_new@example.com");
            assertThat(response.getRole()).isEqualTo("fleet_owner");
            assertThat(response.getIsActive()).isFalse();
            verify(passwordEncoder).encode("newsecret456");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when user id does not exist")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUser(999L, UpdateUserRequest.builder().build()))
                    .isInstanceOf(UserException.UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUserTests {

        @Test
        @DisplayName("Successfully deletes user when no cargo or booking history exists")
        void deletesUserWithoutHistory() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
            when(freightRepository.findByShipper_Id(10L)).thenReturn(Collections.emptyList());
            when(bookingRepository.findByDriver_Id(10L)).thenReturn(Collections.emptyList());
            when(bookingRepository.findByShipperId(10L)).thenReturn(Collections.emptyList());

            userService.deleteUser(10L);

            verify(userRepository).delete(sampleUser);
        }

        @Test
        @DisplayName("Throws UserHasHistoryException (409) when user has cargo history")
        void throwsWhenUserHasCargoHistory() {
            when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
            when(freightRepository.findByShipper_Id(10L)).thenReturn(List.of(mock(com.ethioloadai.freight.entity.Freight.class)));

            assertThatThrownBy(() -> userService.deleteUser(10L))
                    .isInstanceOf(UserException.UserHasHistoryException.class)
                    .hasMessageContaining("Cannot delete user: they have cargo or booking history");

            verify(userRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("createDriver")
    class CreateDriverTests {

        @Test
        @DisplayName("Creates driver and returns user and driver envelope")
        void createsDriverSuccessfully() {
            CreateDriverAdminRequest request = CreateDriverAdminRequest.builder()
                    .name("Driver Dawit")
                    .email("dawit@example.com")
                    .phone("+251933333333")
                    .password("driverpwd")
                    .licenseNumber("ETH-12345")
                    .nationalId("NAT-999")
                    .yearsExperience(5)
                    .role("driver")
                    .build();

            when(userRepository.existsByEmail("dawit@example.com")).thenReturn(false);
            when(userRepository.existsByPhone("+251933333333")).thenReturn(false);
            when(passwordEncoder.encode("driverpwd")).thenReturn("hashed_drv");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(25L);
                return u;
            });

            Map<String, Object> result = userService.createDriver(request);

            assertThat(result).containsKey("user");
            assertThat(result).containsKey("driver");
            UserResponse userResp = (UserResponse) result.get("user");
            assertThat(userResp.getId()).isEqualTo(25L);
            assertThat(userResp.getRole()).isEqualTo("driver");
        }
    }
}

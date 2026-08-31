package com.ethioloadai.auth.mapper;

import com.ethioloadai.auth.dto.AuthenticationResponse;
import com.ethioloadai.auth.dto.RegisterRequest;
import com.ethioloadai.auth.dto.UpdateProfileRequest;
import com.ethioloadai.auth.dto.UserResponse;
import com.ethioloadai.user.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "fleetOwnerId", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "rememberToken", ignore = true)
    @Mapping(target = "role", source = "role", qualifiedByName = "mapRole")
    @Mapping(target = "verificationStatus", expression = "java(calculateVerificationStatus(request.getRole()))")
    @Mapping(target = "isActive", expression = "java(calculateIsActive(request.getRole()))")
    User toEntity(RegisterRequest request);

    UserResponse toResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "verificationStatus", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "fleetOwnerId", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    @Mapping(target = "rememberToken", ignore = true)
    void updateEntityFromDto(UpdateProfileRequest request, @MappingTarget User user);

    @Named("calculateVerificationStatus")
    default Boolean calculateVerificationStatus(String role) {
        return !"driver".equalsIgnoreCase(role);
    }

    @Named("calculateIsActive")
    default Boolean calculateIsActive(String role) {
        return !"driver".equalsIgnoreCase(role);
    }

    @Named("mapRole")
    default User.Role mapRole(String role) {
        if (role == null) {
            return null;
        }
        try {
            return User.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role + ". Valid roles are: shipper, driver, admin, fleet_owner");
        }
    }
}

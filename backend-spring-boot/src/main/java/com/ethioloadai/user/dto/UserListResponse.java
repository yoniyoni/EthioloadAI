package com.ethioloadai.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * List response matching legacy /users envelope expected by admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {

    private List<UserResponse> users;
    private Long total;
}

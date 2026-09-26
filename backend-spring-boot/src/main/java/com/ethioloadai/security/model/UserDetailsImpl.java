package com.ethioloadai.security.model;

import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@RequiredArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private final User user;

    public User getUser() { return user; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = user.getRole().name();
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        // Return email as username for Spring Security
        return user.getEmail() != null ? user.getEmail() : user.getPhone();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        if (user.getIsActive() == null || user.getIsActive()) return true;
        // Pending drivers (is_active=false, KYC not yet verified) must be able to
        // authenticate so they can upload their required KYC documents.
        // This mirrors the identical exemption in AuthenticationServiceImpl.login().
        // All other inactive accounts (shippers, fleet owners, suspended users) remain blocked.
        return user.getRole() == User.Role.DRIVER
                && !Boolean.TRUE.equals(user.getVerificationStatus());
    }
}

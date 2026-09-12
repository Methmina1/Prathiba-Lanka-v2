package com.example.PrathibaLanka.security;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long userId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    /** Build a principal from an Admin entity. */
    public static UserPrincipal fromAdmin(Admin admin) {
        return new UserPrincipal(
                admin.getAdminId(),
                admin.getEmail(),
                admin.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    /** Build a principal from a Customer entity. */
    public static UserPrincipal fromCustomer(Customer customer) {
        return new UserPrincipal(
                customer.getCustomerId(),
                customer.getEmail(),
                customer.getPasswordHash() != null ? customer.getPasswordHash() : "",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );
    }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
package com.bugpilot.security;

import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class UserPrincipalTest {

    @Test
    void testAuthoritiesForAdmin() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPassword("secret");
        user.setRole(Role.ADMIN);

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertEquals("admin@example.com", principal.getUsername());
        assertEquals("secret", principal.getPassword());
    }

    @Test
    void testAuthoritiesForDeveloper() {
        User user = new User();
        user.setEmail("dev@example.com");
        user.setRole(Role.DEVELOPER);

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_DEVELOPER")));
    }

    @Test
    void testAuthoritiesForTester() {
        User user = new User();
        user.setEmail("tester@example.com");
        user.setRole(Role.TESTER);

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertEquals(1, authorities.size());
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_TESTER")));
    }

    @Test
    void testAuthoritiesForNullRole() {
        User user = new User();
        user.setEmail("norole@example.com");
        user.setRole(null);

        UserPrincipal principal = new UserPrincipal(user);

        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertTrue(authorities.isEmpty());
    }
}

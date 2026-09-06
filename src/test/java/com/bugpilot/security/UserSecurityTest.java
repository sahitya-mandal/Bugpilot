package com.bugpilot.security;

import com.bugpilot.controller.UserController;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAuthenticationEntryPoint entryPoint;
    private JwtAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        entryPoint = new JwtAuthenticationEntryPoint();
        accessDeniedHandler = new JwtAccessDeniedHandler();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequest_toUsersEndpoint_triggers401EntryPoint() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("No token provided"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 401"));
        assertTrue(response.getContentAsString().contains("Unauthorized"));
    }

    @Test
    void unauthenticatedRequest_toUserByIdEndpoint_triggers401EntryPoint() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("No token provided"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 401"));
        assertTrue(response.getContentAsString().contains("Unauthorized"));
    }

    @Test
    void developerRequest_toUsersEndpoint_triggers403AccessDenied() throws IOException {
        User devUser = new User();
        devUser.setId(2L);
        devUser.setEmail("dev@bugpilot.com");
        devUser.setRole(Role.DEVELOPER);
        UserPrincipal devPrincipal = new UserPrincipal(devUser);

        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(devPrincipal, null, devPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied: insufficient permissions"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 403"));
        assertTrue(response.getContentAsString().contains("Access denied"));

        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DEVELOPER")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void developerRequest_toUserByIdEndpoint_triggers403AccessDenied() throws IOException {
        User devUser = new User();
        devUser.setId(2L);
        devUser.setEmail("dev@bugpilot.com");
        devUser.setRole(Role.DEVELOPER);
        UserPrincipal devPrincipal = new UserPrincipal(devUser);

        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(devPrincipal, null, devPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied: insufficient permissions"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 403"));
        assertTrue(response.getContentAsString().contains("Access denied"));
    }

    @Test
    void testerRequest_toUsersEndpoint_triggers403AccessDenied() throws IOException {
        User testerUser = new User();
        testerUser.setId(3L);
        testerUser.setEmail("tester@bugpilot.com");
        testerUser.setRole(Role.TESTER);
        UserPrincipal testerPrincipal = new UserPrincipal(testerUser);

        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(testerPrincipal, null, testerPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied: insufficient permissions"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 403"));
        assertTrue(response.getContentAsString().contains("Access denied"));

        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_TESTER")));
        assertFalse(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testerRequest_toUserByIdEndpoint_triggers403AccessDenied() throws IOException {
        User testerUser = new User();
        testerUser.setId(3L);
        testerUser.setEmail("tester@bugpilot.com");
        testerUser.setRole(Role.TESTER);
        UserPrincipal testerPrincipal = new UserPrincipal(testerUser);

        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(testerPrincipal, null, testerPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(request, response, new AccessDeniedException("Access denied: insufficient permissions"));

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"status\": 403"));
        assertTrue(response.getContentAsString().contains("Access denied"));
    }

    @Test
    void adminRequest_hasAdminAuthority() {
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@bugpilot.com");
        adminUser.setRole(Role.ADMIN);
        UserPrincipal adminPrincipal = new UserPrincipal(adminUser);

        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(adminPrincipal, null, adminPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void userController_isGuardedByAdminPreAuthorize() {
        PreAuthorize annotation = UserController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "UserController must be annotated with @PreAuthorize");
        assertEquals("hasRole('ADMIN')", annotation.value(), "@PreAuthorize must require ROLE_ADMIN");
    }

    @Test
    void userResponse_neverSerializesPasswordOrSensitiveAuthenticationFields() throws Exception {
        UserResponse response = new UserResponse();
        response.setId(42L);
        response.setName("Test User");
        response.setEmail("test@bugpilot.com");
        response.setRole(Role.DEVELOPER);

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Verify allowed fields
        assertEquals(42L, root.get("id").asLong());
        assertEquals("Test User", root.get("name").asText());
        assertEquals("test@bugpilot.com", root.get("email").asText());
        assertEquals("DEVELOPER", root.get("role").asText());

        // Verify sensitive fields NEVER exist
        assertNull(root.get("password"), "password must not be serialized");
        assertNull(root.get("passwordHash"), "passwordHash must not be serialized");
        assertNull(root.get("token"), "token must not be serialized");
        assertNull(root.get("jwt"), "jwt must not be serialized");
        assertNull(root.get("credentials"), "credentials must not be serialized");
        assertNull(root.get("secret"), "secret must not be serialized");
        assertNull(root.get("authorities"), "authorities must not be serialized");

        assertFalse(json.toLowerCase().contains("password"));
        assertFalse(json.toLowerCase().contains("hash"));
        assertFalse(json.toLowerCase().contains("token"));
        assertFalse(json.toLowerCase().contains("credential"));
    }
}

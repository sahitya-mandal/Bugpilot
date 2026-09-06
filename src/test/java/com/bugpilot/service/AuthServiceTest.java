package com.bugpilot.service;

import com.bugpilot.dto.LoginRequest;
import com.bugpilot.dto.LoginResponse;
import com.bugpilot.dto.RegisterRequest;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.exception.EmailAlreadyExistsException;
import com.bugpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_whenValid_hashesPasswordAndReturnsUserResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("securePassword123");
        request.setRole(Role.TESTER);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.findFirstByEmailOrderByIdDesc("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePassword123")).thenReturn("$2a$10$hashedBcryptString");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setName("Alice");
        savedUser.setEmail("alice@example.com");
        savedUser.setPassword("$2a$10$hashedBcryptString");
        savedUser.setRole(Role.TESTER);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals(Role.TESTER, response.getRole());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("$2a$10$hashedBcryptString", captor.getValue().getPassword());
    }

    @Test
    void register_whenDuplicateEmail_throwsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Bob");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_whenAdminRoleRequested_forcesDeveloperRole() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Attacker");
        request.setEmail("attacker@example.com");
        request.setPassword("password123");
        request.setRole(Role.ADMIN);

        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
        when(userRepository.findFirstByEmailOrderByIdDesc("attacker@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");

        User savedUser = new User();
        savedUser.setId(11L);
        savedUser.setName("Attacker");
        savedUser.setEmail("attacker@example.com");
        savedUser.setPassword("$2a$10$hashed");
        savedUser.setRole(Role.DEVELOPER);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertEquals(Role.DEVELOPER, response.getRole());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Role.DEVELOPER, captor.getValue().getRole());
    }

    @Test
    void login_whenCredentialsAreValid_returnsJwtTokenAndSafeUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("correctPassword");

        User user = new User();
        user.setId(5L);
        user.setName("John");
        user.setEmail("user@example.com");
        user.setPassword("hashedPassword");
        user.setRole(Role.ADMIN);

        when(userRepository.findFirstByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPassword", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken("user@example.com", Role.ADMIN)).thenReturn("mocked.jwt.token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(5L, response.getUser().getId());
        assertEquals("John", response.getUser().getName());
        assertEquals("user@example.com", response.getUser().getEmail());
        assertEquals(Role.ADMIN, response.getUser().getRole());
    }

    @Test
    void login_whenUserNotFound_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("nonexistent@example.com");
        request.setPassword("password");

        when(userRepository.findFirstByEmailOrderByIdDesc("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void login_whenPasswordDoesNotMatch_throwsBadCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrongPassword");

        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("hashedPassword");

        when(userRepository.findFirstByEmailOrderByIdDesc("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}

package com.bugpilot.service;

import com.bugpilot.dto.UserRequest;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.exception.EmailAlreadyExistsException;
import com.bugpilot.exception.UserNotFoundException;
import com.bugpilot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void createUser_whenEmailDoesNotExist_createsUserSuccessfully() {
        UserRequest request = new UserRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("plainPassword");
        request.setRole(Role.DEVELOPER);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plainPassword")).thenReturn("hashedPassword");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setName("Alice");
        savedUser.setEmail("alice@example.com");
        savedUser.setPassword("hashedPassword");
        savedUser.setRole(Role.DEVELOPER);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals(Role.DEVELOPER, response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void createUser_whenEmailAlreadyExists_throwsEmailAlreadyExistsException() {
        UserRequest request = new UserRequest();
        request.setName("Bob");
        request.setEmail("bob@example.com");
        request.setPassword("password123");

        User existingUser = new User();
        existingUser.setEmail("bob@example.com");

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.createUser(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserById_whenUserDoesNotExist_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById(99L));
    }
}

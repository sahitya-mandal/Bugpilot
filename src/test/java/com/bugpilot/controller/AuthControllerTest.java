package com.bugpilot.controller;

import com.bugpilot.dto.LoginRequest;
import com.bugpilot.dto.LoginResponse;
import com.bugpilot.dto.RegisterRequest;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.enums.Role;
import com.bugpilot.exception.EmailAlreadyExistsException;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_withValidData_returns201Created() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Charlie");
        request.setEmail("charlie@example.com");
        request.setPassword("password123");
        request.setRole(Role.TESTER);

        UserResponse response = new UserResponse();
        response.setId(15L);
        response.setName("Charlie");
        response.setEmail("charlie@example.com");
        response.setRole(Role.TESTER);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.name").value("Charlie"))
                .andExpect(jsonPath("$.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.role").value("TESTER"));
    }

    @Test
    void register_withDuplicateEmail_returns409Conflict() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Charlie");
        request.setEmail("charlie@example.com");
        request.setPassword("password123");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("charlie@example.com"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email charlie@example.com is already registered."));
    }

    @Test
    void register_withInvalidPassword_returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Charlie");
        request.setEmail("charlie@example.com");
        request.setPassword("123"); // less than 6 chars

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withBlankName_returns400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("");
        request.setEmail("charlie@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withValidCredentials_returns200OkWithTokenAndUser() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("charlie@example.com");
        request.setPassword("password123");

        UserResponse userResponse = new UserResponse();
        userResponse.setId(15L);
        userResponse.setName("Charlie");
        userResponse.setEmail("charlie@example.com");
        userResponse.setRole(Role.TESTER);

        LoginResponse response = new LoginResponse("sample.jwt.token", userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("sample.jwt.token"))
                .andExpect(jsonPath("$.user.id").value(15))
                .andExpect(jsonPath("$.user.name").value("Charlie"))
                .andExpect(jsonPath("$.user.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.user.role").value("TESTER"));
    }

    @Test
    void login_withBadCredentials_returns401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("charlie@example.com");
        request.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_withBlankEmail_returns400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("");
        request.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

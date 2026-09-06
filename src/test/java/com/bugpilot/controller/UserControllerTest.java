package com.bugpilot.controller;

import com.bugpilot.dto.UserRequest;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.enums.Role;
import com.bugpilot.exception.GlobalExceptionHandler;
import com.bugpilot.exception.UserNotFoundException;
import com.bugpilot.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllUsers_returnsUserResponsesWithoutSensitiveFields() throws Exception {
        UserResponse user1 = new UserResponse();
        user1.setId(1L);
        user1.setName("Alice Admin");
        user1.setEmail("alice@bugpilot.com");
        user1.setRole(Role.ADMIN);

        UserResponse user2 = new UserResponse();
        user2.setId(2L);
        user2.setName("Bob Developer");
        user2.setEmail("bob@bugpilot.com");
        user2.setRole(Role.DEVELOPER);

        when(userService.getAllUsers()).thenReturn(List.of(user1, user2));

        mockMvc.perform(get("/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice Admin"))
                .andExpect(jsonPath("$[0].email").value("alice@bugpilot.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].credentials").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Bob Developer"))
                .andExpect(jsonPath("$[1].email").value("bob@bugpilot.com"))
                .andExpect(jsonPath("$[1].role").value("DEVELOPER"))
                .andExpect(jsonPath("$[1].password").doesNotExist())
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].token").doesNotExist())
                .andExpect(jsonPath("$[1].credentials").doesNotExist());

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void getUserById_whenUserExists_returnsUserResponseWithoutSensitiveFields() throws Exception {
        UserResponse user = new UserResponse();
        user.setId(5L);
        user.setName("Charlie Tester");
        user.setEmail("charlie@bugpilot.com");
        user.setRole(Role.TESTER);

        when(userService.getUserById(5L)).thenReturn(user);

        mockMvc.perform(get("/users/5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Charlie Tester"))
                .andExpect(jsonPath("$.email").value("charlie@bugpilot.com"))
                .andExpect(jsonPath("$.role").value("TESTER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.credentials").doesNotExist());

        verify(userService, times(1)).getUserById(5L);
    }

    @Test
    void getUserById_whenUserDoesNotExist_returns404NotFound() throws Exception {
        when(userService.getUserById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User with id 99 not found."));

        verify(userService, times(1)).getUserById(99L);
    }

    @Test
    void createUser_withValidPayload_returnsUserResponseWithoutPassword() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("Dave");
        request.setEmail("dave@bugpilot.com");
        request.setPassword("SecretPassword123!");
        request.setRole(Role.DEVELOPER);

        UserResponse response = new UserResponse();
        response.setId(10L);
        response.setName("Dave");
        response.setEmail("dave@bugpilot.com");
        response.setRole(Role.DEVELOPER);

        when(userService.createUser(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Dave"))
                .andExpect(jsonPath("$.email").value("dave@bugpilot.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService, times(1)).createUser(any(UserRequest.class));
    }

    @Test
    void updateUser_withValidPayload_returnsUpdatedUserResponse() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("Dave Updated");
        request.setEmail("dave_updated@bugpilot.com");
        request.setPassword("NewPassword123!");
        request.setRole(Role.ADMIN);

        UserResponse response = new UserResponse();
        response.setId(10L);
        response.setName("Dave Updated");
        response.setEmail("dave_updated@bugpilot.com");
        response.setRole(Role.ADMIN);

        when(userService.updateUser(eq(10L), any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(put("/users/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Dave Updated"))
                .andExpect(jsonPath("$.email").value("dave_updated@bugpilot.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService, times(1)).updateUser(eq(10L), any(UserRequest.class));
    }

    @Test
    void deleteUser_returnsConfirmationMessage() throws Exception {
        doNothing().when(userService).deleteUser(10L);

        mockMvc.perform(delete("/users/10"))
                .andExpect(status().isOk())
                .andExpect(content().string("User deleted successfully."));

        verify(userService, times(1)).deleteUser(10L);
    }
}

package com.bugpilot.service;

import com.bugpilot.dto.LoginRequest;
import com.bugpilot.dto.LoginResponse;
import com.bugpilot.dto.RegisterRequest;
import com.bugpilot.dto.UserResponse;
import com.bugpilot.entity.User;
import com.bugpilot.enums.Role;
import com.bugpilot.exception.EmailAlreadyExistsException;
import com.bugpilot.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest registerRequest) {

        // Enforce duplicate-email prevention at application layer
        if (userRepository.existsByEmail(registerRequest.getEmail()) ||
                userRepository.findFirstByEmailOrderByIdDesc(registerRequest.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(registerRequest.getEmail());
        }

        // Public registration allows DEVELOPER or TESTER; ADMIN is strictly forbidden for self-registration
        Role assignedRole = registerRequest.getRole();
        if (assignedRole == null || assignedRole == Role.ADMIN) {
            assignedRole = Role.DEVELOPER;
        }

        User user = new User();
        user.setName(registerRequest.getName().trim());
        user.setEmail(registerRequest.getEmail().trim().toLowerCase());
        // Password must ALWAYS be BCrypt hashed before persistence
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(assignedRole);

        User savedUser = userRepository.save(user);

        UserResponse response = new UserResponse();
        response.setId(savedUser.getId());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setRole(savedUser.getRole());

        return response;
    }

    public LoginResponse login(LoginRequest loginRequest) {

        // Uses temporary compatibility query to handle existing duplicate rows safely
        User user = userRepository.findFirstByEmailOrderByIdDesc(loginRequest.getEmail().trim().toLowerCase())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid email or password"));

        boolean passwordMatches = passwordEncoder.matches(
                loginRequest.getPassword(),
                user.getPassword()
        );

        if (!passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole());

        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setName(user.getName());
        userResponse.setEmail(user.getEmail());
        userResponse.setRole(user.getRole());

        return new LoginResponse(token, userResponse);
    }
}
package com.bugpilot.service;

import com.bugpilot.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 256-bit test key for unit testing
        ReflectionTestUtils.setField(jwtService, "secret", "BugPilotTestSecretKeyForTestingPurposesOnly2026Need32Bytes");
        ReflectionTestUtils.setField(jwtService, "expirationTime", 3600000L);
    }

    @Test
    void testGenerateTokenAndExtractEmail() {
        String token = jwtService.generateToken("test@example.com", Role.ADMIN);

        assertNotNull(token);
        assertEquals("test@example.com", jwtService.extractEmail(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void testGenerateTokenWithDeveloperRole() {
        String token = jwtService.generateToken("dev@example.com", Role.DEVELOPER);

        assertNotNull(token);
        assertEquals("dev@example.com", jwtService.extractEmail(token));
        assertEquals("DEVELOPER", jwtService.extractRole(token));
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.token.here"));
    }
}

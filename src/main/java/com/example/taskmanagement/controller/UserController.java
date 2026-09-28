package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.ApiResponse;
import com.example.taskmanagement.dto.UserResponseDTO;
import com.example.taskmanagement.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getAuthenticatedUser(Authentication authentication) {
        // Our User entity is the UserDetails principal; map it to a DTO so the password hash never leaves.
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Current user", new UserResponseDTO(currentUser)));
    }
}

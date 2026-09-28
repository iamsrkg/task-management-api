package com.example.taskmanagement.dto;

import com.example.taskmanagement.entity.Role;
import com.example.taskmanagement.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/** Public view of a user. Never expose the entity itself: it carries the password hash. */
public record UserResponseDTO(UUID id, String email, Role role, LocalDateTime createdAt) {

    public UserResponseDTO(User user) {
        this(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}

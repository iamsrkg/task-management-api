package com.example.taskmanagement.dto;

import com.example.taskmanagement.entity.Project;
import java.time.LocalDateTime;
import java.util.UUID;

public class ProjectResponseDTO {

    private UUID id;
    private String name;
    private String description;
    private String ownerEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProjectResponseDTO() {}

    public ProjectResponseDTO(Project project) {
        this.id = project.getId();
        this.name = project.getName();
        this.description = project.getDescription();
        this.ownerEmail = project.getOwner().getEmail();
        this.createdAt = project.getCreatedAt();
        this.updatedAt = project.getUpdatedAt();
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwnerEmail() { return ownerEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

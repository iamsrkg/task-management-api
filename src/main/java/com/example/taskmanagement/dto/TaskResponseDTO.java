package com.example.taskmanagement.dto;

import com.example.taskmanagement.entity.Task;
import com.example.taskmanagement.entity.TaskStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public class TaskResponseDTO {

    private UUID id;
    private String title;
    private String description;
    private TaskStatus status;
    private UUID projectId;
    private String assigneeEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TaskResponseDTO() {}

    public TaskResponseDTO(Task task) {
        this.id = task.getId();
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getStatus();
        this.projectId = task.getProject().getId();
        this.assigneeEmail = task.getAssignee() != null ? task.getAssignee().getEmail() : null;
        this.createdAt = task.getCreatedAt();
        this.updatedAt = task.getUpdatedAt();
    }

    // Getters
    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public UUID getProjectId() { return projectId; }
    public String getAssigneeEmail() { return assigneeEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

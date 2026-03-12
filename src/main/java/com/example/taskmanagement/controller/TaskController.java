package com.example.taskmanagement.controller;

import com.example.taskmanagement.dto.ApiResponse;
import com.example.taskmanagement.dto.TaskRequestDTO;
import com.example.taskmanagement.dto.TaskResponseDTO;
import com.example.taskmanagement.entity.TaskStatus;
import com.example.taskmanagement.entity.User;
import com.example.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponseDTO>> createTask(
            @Valid @RequestBody TaskRequestDTO request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponseDTO createdTask = taskService.createTask(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created successfully", createdTask));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> updateTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskRequestDTO request,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponseDTO updatedTask = taskService.updateTask(taskId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Task updated successfully", updatedTask));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        taskService.deleteTask(taskId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Task deleted successfully", null));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> changeStatus(
            @PathVariable UUID taskId,
            @RequestParam TaskStatus status,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponseDTO updatedTask = taskService.changeStatus(taskId, status, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Task status updated", updatedTask));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponseDTO>>> getTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        
        // Parse sort parameter (e.g. "createdAt,desc")
        Sort.Direction direction = sort[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));
        
        Page<TaskResponseDTO> tasks = taskService.getTasks(status, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Tasks fetched successfully", tasks));
    }
}

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

import java.util.Set;
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

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponseDTO>> getTask(
            @PathVariable UUID taskId,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success("Task fetched successfully", taskService.getTask(taskId, currentUser)));
    }

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "title", "status");
    private static final int MAX_PAGE_SIZE = 100;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponseDTO>>> getTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort,
            Authentication authentication
    ) {
        User currentUser = (User) authentication.getPrincipal();

        // Sort comes from user input, so only whitelisted fields reach the query.
        String field = sort[0];
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("sort field must be one of " + SORTABLE_FIELDS);
        }
        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, field));

        Page<TaskResponseDTO> tasks = taskService.getTasks(status, pageable, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Tasks fetched successfully", tasks));
    }
}

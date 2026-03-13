package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.TaskRequestDTO;
import com.example.taskmanagement.dto.TaskResponseDTO;
import com.example.taskmanagement.entity.Project;
import com.example.taskmanagement.entity.Task;
import com.example.taskmanagement.exception.ResourceNotFoundException;
import com.example.taskmanagement.exception.UnauthorizedException;
import com.example.taskmanagement.repository.ProjectRepository;
import com.example.taskmanagement.repository.TaskRepository;
import com.example.taskmanagement.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public TaskResponseDTO createTask(TaskRequestDTO request, User authenticatedUser) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.getProjectId()));

        // Extra Validation: User must own the project to add a task to it
        if (!project.getOwner().getId().equals(authenticatedUser.getId())) {
            throw new UnauthorizedException("Unauthorized: You do not own this project");
        }

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setProject(project);

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found with id: " + request.getAssigneeId()));
            task.setAssignee(assignee);
        }

        Task savedTask = taskRepository.save(task);
        return new TaskResponseDTO(savedTask);
    }

    public TaskResponseDTO updateTask(UUID taskId, TaskRequestDTO request, User authenticatedUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        if (!task.getProject().getOwner().getId().equals(authenticatedUser.getId())) {
            throw new UnauthorizedException("Unauthorized to update this task");
        }

        // Optimistic Locking Check
        if (request.getVersion() != null && !request.getVersion().equals(task.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Task.class, task.getId());
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        if (request.getAssigneeId() != null) {
            User assignee = userRepository.findById(request.getAssigneeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found with id: " + request.getAssigneeId()));
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }

        return new TaskResponseDTO(taskRepository.save(task));
    }

    public void deleteTask(UUID taskId, User authenticatedUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        if (!task.getProject().getOwner().getId().equals(authenticatedUser.getId())) {
            throw new UnauthorizedException("Unauthorized to delete this task");
        }

        taskRepository.delete(task);
    }

    public TaskResponseDTO changeStatus(UUID taskId, TaskStatus status, User authenticatedUser) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        // Either the owner of the project OR the assigned user can update the status
        boolean isOwner = task.getProject().getOwner().getId().equals(authenticatedUser.getId());
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(authenticatedUser.getId());

        if (!isOwner && !isAssignee) {
            throw new UnauthorizedException("Unauthorized to change task status");
        }

        task.setStatus(status);
        return new TaskResponseDTO(taskRepository.save(task));
    }

    public Page<TaskResponseDTO> getTasks(TaskStatus status, Pageable pageable, User authenticatedUser) {
        Page<Task> tasksPage;

        if (status != null) {
            tasksPage = taskRepository.findByProjectOwnerIdAndStatus(authenticatedUser.getId(), status, pageable);
        } else {
            tasksPage = taskRepository.findByProjectOwnerId(authenticatedUser.getId(), pageable);
        }

        return tasksPage.map(TaskResponseDTO::new);
    }
}

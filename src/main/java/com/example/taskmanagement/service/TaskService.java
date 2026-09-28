package com.example.taskmanagement.service;

import com.example.taskmanagement.dto.TaskRequestDTO;
import com.example.taskmanagement.dto.TaskResponseDTO;
import com.example.taskmanagement.entity.Project;
import com.example.taskmanagement.entity.Task;
import com.example.taskmanagement.entity.TaskStatus;
import com.example.taskmanagement.entity.User;
import com.example.taskmanagement.exception.ResourceNotFoundException;
import com.example.taskmanagement.exception.UnauthorizedException;
import com.example.taskmanagement.repository.ProjectRepository;
import com.example.taskmanagement.repository.TaskRepository;
import com.example.taskmanagement.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public TaskResponseDTO createTask(TaskRequestDTO request, User authenticatedUser) {
        // Only the caller's own projects are visible; anything else is "not found".
        Project project = projectRepository.findById(request.getProjectId())
                .filter(p -> p.getOwner().getId().equals(authenticatedUser.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + request.getProjectId()));

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

        return new TaskResponseDTO(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public TaskResponseDTO getTask(UUID taskId, User authenticatedUser) {
        return new TaskResponseDTO(findVisible(taskId, authenticatedUser));
    }

    @Transactional
    public TaskResponseDTO updateTask(UUID taskId, TaskRequestDTO request, User authenticatedUser) {
        if (request.getVersion() == null) {
            throw new IllegalArgumentException("version is required when updating a task (optimistic locking)");
        }

        Task task = findVisible(taskId, authenticatedUser);
        requireOwner(task, authenticatedUser, "Only the project owner can edit this task");

        if (!request.getVersion().equals(task.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Task.class, task.getId());
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

        // saveAndFlush so a concurrent writer that slipped in after our read still fails on @Version.
        return new TaskResponseDTO(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void deleteTask(UUID taskId, User authenticatedUser) {
        Task task = findVisible(taskId, authenticatedUser);
        requireOwner(task, authenticatedUser, "Only the project owner can delete this task");
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponseDTO changeStatus(UUID taskId, TaskStatus status, User authenticatedUser) {
        // Owners and assignees can both move a task through its statuses.
        Task task = findVisible(taskId, authenticatedUser);
        task.setStatus(status);
        return new TaskResponseDTO(taskRepository.saveAndFlush(task));
    }

    @Transactional(readOnly = true)
    public Page<TaskResponseDTO> getTasks(TaskStatus status, Pageable pageable, User authenticatedUser) {
        Page<Task> tasksPage = status != null
                ? taskRepository.findVisibleByStatus(authenticatedUser.getId(), status, pageable)
                : taskRepository.findVisible(authenticatedUser.getId(), pageable);
        return tasksPage.map(TaskResponseDTO::new);
    }

    private Task findVisible(UUID taskId, User user) {
        return taskRepository.findVisibleById(taskId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));
    }

    // Visible but not owned (e.g. an assignee) is a genuine permission problem: 403 is honest here.
    private void requireOwner(Task task, User user, String message) {
        if (!task.getProject().getOwner().getId().equals(user.getId())) {
            throw new UnauthorizedException(message);
        }
    }
}

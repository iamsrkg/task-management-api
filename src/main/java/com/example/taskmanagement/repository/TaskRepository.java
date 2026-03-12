package com.example.taskmanagement.repository;

import com.example.taskmanagement.entity.Task;
import com.example.taskmanagement.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectId(UUID projectId);
    
    // Day 6: Pagination and Status Filtering
    Page<Task> findByProjectOwnerId(UUID ownerId, Pageable pageable);
    Page<Task> findByProjectOwnerIdAndStatus(UUID ownerId, TaskStatus status, Pageable pageable);
    
    Page<Task> findByAssigneeId(UUID assigneeId, Pageable pageable);
    Page<Task> findByAssigneeIdAndStatus(UUID assigneeId, TaskStatus status, Pageable pageable);
}

package com.example.taskmanagement.repository;

import com.example.taskmanagement.entity.Task;
import com.example.taskmanagement.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectId(UUID projectId);

    // Tenant scoping lives in the query itself: a task outside the caller's tenant
    // is indistinguishable from one that does not exist (404, not 403).
    @Query("""
            select t from Task t left join t.assignee a
            where t.id = :id and (t.project.owner.id = :userId or a.id = :userId)
            """)
    Optional<Task> findVisibleById(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query(value = """
            select t from Task t left join t.assignee a
            where t.project.owner.id = :userId or a.id = :userId
            """,
            countQuery = """
            select count(t) from Task t left join t.assignee a
            where t.project.owner.id = :userId or a.id = :userId
            """)
    Page<Task> findVisible(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            select t from Task t left join t.assignee a
            where (t.project.owner.id = :userId or a.id = :userId) and t.status = :status
            """,
            countQuery = """
            select count(t) from Task t left join t.assignee a
            where (t.project.owner.id = :userId or a.id = :userId) and t.status = :status
            """)
    Page<Task> findVisibleByStatus(@Param("userId") UUID userId, @Param("status") TaskStatus status, Pageable pageable);
}

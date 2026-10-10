-- Users, projects and tasks. Hibernate validates the entities against this and never changes it.
create table users (
    id         uuid primary key,
    email      varchar(255) not null unique,
    password   varchar(255) not null,
    role       varchar(255) not null,
    created_at timestamp(6),
    updated_at timestamp(6)
);

create table projects (
    id          uuid primary key,
    name        varchar(255) not null,
    description text,
    owner_id    uuid not null references users (id),
    created_at  timestamp(6),
    updated_at  timestamp(6)
);

create table tasks (
    id          uuid primary key,
    version     bigint,
    title       varchar(255) not null,
    description text,
    status      varchar(255) not null,
    project_id  uuid not null references projects (id),
    assignee_id uuid references users (id),
    created_at  timestamp(6),
    updated_at  timestamp(6)
);

-- every list and lookup filters by owner or assignee, so both sides of that check are indexed
create index idx_projects_owner on projects (owner_id);
create index idx_tasks_project on tasks (project_id);
create index idx_tasks_assignee on tasks (assignee_id);

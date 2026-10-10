-- One row per refresh token ever issued. Only a SHA-256 hash is stored, so a copy of this
-- table can't be used to sign in.
create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid not null references users (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamp(6) not null,
    revoked_at timestamp(6),
    created_at timestamp(6) not null
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);

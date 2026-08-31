-- Identidade do usuario.
--
-- nickname e o identificador PUBLICO: e por ele que as pessoas se encontram.
-- email e obrigatorio e unico, mas PRIVADO e nao verificado nesta versao.
-- Ver DECISOES.md, ADR-001, na raiz do repositorio.
--
-- Ambos sao guardados ja normalizados em minusculas, entao o UNIQUE simples
-- resolve a comparacao case-insensitive sem indice funcional.

create table users (
    id            uuid         primary key,
    nickname      varchar(30)  not null unique,
    email         varchar(254) not null unique,
    password_hash varchar(255) not null,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

-- Refresh tokens com rotacao e deteccao de reuso.
--
-- O token em claro nunca e guardado: so o SHA-256 dele em hex. Cada refresh
-- emite um token novo e marca o anterior como substituido. Se um token ja
-- substituido for apresentado de novo, e sinal de vazamento, e a familia
-- inteira (family_id) e revogada de uma vez.

create table refresh_tokens (
    id           uuid        primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    token_hash   varchar(64) not null unique,
    family_id    uuid        not null,
    device_label varchar(80),
    issued_at    timestamptz not null default now(),
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    replaced_by  uuid        references refresh_tokens (id)
);

create index ix_refresh_tokens_user   on refresh_tokens (user_id);
create index ix_refresh_tokens_family on refresh_tokens (family_id);

-- Conversa entre duas pessoas: mensagens de texto + trilha de auditoria dos
-- compartilhamentos. As duas coisas aparecem na mesma timeline na tela de
-- Conversa, entao a leitura junta as duas tabelas por tempo.

create table messages (
    id           uuid        primary key,
    sender_id    uuid        not null references users (id) on delete cascade,
    recipient_id uuid        not null references users (id) on delete cascade,
    body         text        not null,
    created_at   timestamptz not null default now(),
    delivered_at timestamptz,
    read_at      timestamptz,
    check (sender_id <> recipient_id)
);

create index ix_messages_sender    on messages (sender_id, created_at desc);
create index ix_messages_recipient on messages (recipient_id, created_at desc);

-- ---------------------------------------------------------------------------
-- Auditoria de grant: cada mudanca vira um evento visivel na conversa
-- ("Voce comecou a compartilhar Bateria", "Voce liberou Telegram").
-- ---------------------------------------------------------------------------

create table grant_audit (
    id         uuid        primary key,
    grant_id   uuid        not null references grants (id) on delete cascade,
    actor_id   uuid        not null references users (id),
    -- offered | requested | activated | paused | resumed | revoked | rules_changed
    action     varchar(32) not null,
    detail     varchar(200),
    created_at timestamptz not null default now()
);

create index ix_grant_audit_grant on grant_audit (grant_id, created_at);

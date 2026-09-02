-- Bloqueio entre usuarios. Direcional: uma linha "A bloqueou B".
--
-- Efeitos (verificados na aplicacao, nao no banco):
--  - qualquer sentido bloqueado impede troca de mensagens
--  - quem tenta mandar mensagem para quem o bloqueou recebe um erro que diz
--    explicitamente que foi bloqueado

create table blocks (
    id         uuid        primary key,
    blocker_id uuid        not null references users (id) on delete cascade,
    blocked_id uuid        not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    check (blocker_id <> blocked_id),
    unique (blocker_id, blocked_id)
);

create index ix_blocks_blocker on blocks (blocker_id);
create index ix_blocks_blocked on blocks (blocked_id);

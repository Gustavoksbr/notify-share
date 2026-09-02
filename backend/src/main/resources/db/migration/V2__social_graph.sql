-- Grafo social: amizade, compartilhamentos (grants), regras por app e aparelhos.
--
-- A amizade e o pre-requisito de qualquer compartilhamento. Ela e simetrica:
-- uma linha por par, na direcao de quem pediu. O compartilhamento, ao contrario,
-- e direcional — A->B e um grant diferente de B->A — porque a tela separa
-- "compartilho com" de "recebo de".

-- ---------------------------------------------------------------------------
-- Amizade
-- ---------------------------------------------------------------------------

create table friendships (
    id           uuid        primary key,
    requester_id uuid        not null references users (id) on delete cascade,
    addressee_id uuid        not null references users (id) on delete cascade,
    -- pending | accepted. Recusar apaga a linha: nao guardamos "negado".
    status       varchar(16) not null,
    created_at   timestamptz not null default now(),
    responded_at timestamptz,
    check (requester_id <> addressee_id)
);

-- Um vinculo por par, valha qual for a direcao do pedido. least/greatest
-- normalizam o par sem perder quem e o requester na coluna.
create unique index ux_friendships_pair
    on friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

create index ix_friendships_addressee on friendships (addressee_id, status);

-- ---------------------------------------------------------------------------
-- Compartilhamento (grant)
-- ---------------------------------------------------------------------------

create table grants (
    id           uuid        primary key,
    -- quem compartilha as proprias notificacoes
    sharer_id    uuid        not null references users (id) on delete cascade,
    -- quem recebe
    recipient_id uuid        not null references users (id) on delete cascade,
    -- offered_by_sharer | requested_by_recipient | active
    --   | paused_by_sharer | paused_by_recipient | revoked
    status       varchar(24) not null,
    -- quem deu o primeiro passo, so para a UI da caixa de pedidos
    initiated_by uuid        not null references users (id),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    check (sharer_id <> recipient_id),
    -- um grant por sentido: (A->B) e distinto de (B->A)
    unique (sharer_id, recipient_id)
);

create index ix_grants_sharer    on grants (sharer_id, status);
create index ix_grants_recipient on grants (recipient_id, status);

-- ---------------------------------------------------------------------------
-- Regras por app dentro de um grant
-- ---------------------------------------------------------------------------
--
-- package_name guarda o pacote real do app ("com.whatsapp") ou um pseudo-pacote
-- para os eventos que nao vem de app nenhum: "system:battery", "system:wifi".
--
-- Nada e liberado por padrao. Ao aceitar um grant a lista de regras nasce vazia;
-- quem compartilha escolhe cada app na tela de Regras.

create table grant_rules (
    id                uuid        primary key,
    grant_id          uuid        not null references grants (id) on delete cascade,
    package_name      varchar(255) not null,
    -- o toggle da tela: app ligado ou nao
    enabled           boolean     not null default true,
    -- content | sender_only | paused  (o segmented control da tela)
    content_mode      varchar(16) not null default 'content',
    -- true  = todos os remetentes passam
    -- false = so os hashes listados em grant_rule_senders
    all_senders       boolean     not null default true,
    -- so para system:battery — avisar abaixo de X%
    battery_threshold int,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    unique (grant_id, package_name)
);

create index ix_grant_rules_grant on grant_rules (grant_id);

-- Remetentes liberados de um app. sender_hash, nao sender_name: da para filtrar
-- por igualdade sem o servidor saber que a pessoa se chama Jose. sender_label e
-- opcional, so para a UI de quem compartilha, e nunca sai para o destinatario.
create table grant_rule_senders (
    id           uuid        primary key,
    rule_id      uuid        not null references grant_rules (id) on delete cascade,
    sender_hash  varchar(64) not null,
    sender_label varchar(120),
    unique (rule_id, sender_hash)
);

-- ---------------------------------------------------------------------------
-- Aparelhos: token do FCM e presenca
-- ---------------------------------------------------------------------------

create table devices (
    id           uuid         primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    -- token de registro do FCM. Muda sozinho de tempos em tempos; o app reenvia.
    fcm_token    varchar(512) not null unique,
    platform     varchar(16)  not null default 'android',
    device_label varchar(80),
    -- ultimo sinal de vida: heartbeat do app ou ping do WebSocket
    last_seen_at timestamptz  not null default now(),
    created_at   timestamptz  not null default now()
);

create index ix_devices_user on devices (user_id);

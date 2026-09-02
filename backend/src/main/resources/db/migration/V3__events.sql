-- Eventos capturados no aparelho de origem e as entregas para cada destinatario.
--
-- Decisao de schema (ver design/canvas.json, nota "O QUE O FILTRO EXIGE DO BANCO"):
-- os METADADOS ficam em claro e indexados — e por eles que o filtro roda em SQL.
-- So o CONTEUDO e opaco. Hoje ele e JSON em claro; virar ciphertext depois nao
-- mexe no schema, porque o roteamento nunca le essa coluna.

create table events (
    id             uuid         primary key,
    origin_user_id uuid         not null references users (id) on delete cascade,

    -- pacote real ("com.whatsapp") ou pseudo-pacote ("system:battery", "system:wifi")
    package_name   varchar(255) not null,
    -- message | battery | network | system  (dirige as abas do filtro)
    event_type     varchar(24)  not null,
    -- so em mensagens; hash, nunca o nome. NULL nos demais tipos.
    sender_hash    varchar(64),

    occurred_at    timestamptz  not null,
    received_at    timestamptz  not null default now(),

    -- Deduplicacao. O WhatsApp reposta a notificacao com o historico acumulado;
    -- sem isto tudo seria reenviado a cada mensagem nova. A chave e calculada no
    -- aparelho (pacote + hash do remetente + timestamp da ultima mensagem).
    dedup_key      varchar(200),

    -- Conteudo opaco para o servidor. NULL quando a regra e "so remetente".
    content        text,

    unique (origin_user_id, dedup_key)
);

create index ix_events_origin_time on events (origin_user_id, occurred_at desc);
create index ix_events_routing     on events (origin_user_id, package_name, event_type);

-- ---------------------------------------------------------------------------
-- Entregas: uma linha por (evento, grant). Cada destinatario tem o proprio
-- estado de leitura, entao o "Notificacoes" por pessoa se monta daqui.
-- ---------------------------------------------------------------------------

create table event_deliveries (
    id             uuid        primary key,
    event_id       uuid        not null references events (id) on delete cascade,
    grant_id       uuid        not null references grants (id) on delete cascade,
    recipient_id   uuid        not null references users (id) on delete cascade,
    -- modo aplicado no momento da entrega: content | sender_only
    delivered_mode varchar(16) not null,
    created_at     timestamptz not null default now(),
    fcm_message_id varchar(200),
    read_at        timestamptz,
    unique (event_id, grant_id)
);

create index ix_deliveries_recipient_time on event_deliveries (recipient_id, created_at desc);
create index ix_deliveries_event          on event_deliveries (event_id);

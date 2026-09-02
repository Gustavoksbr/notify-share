-- Silenciar por app, do lado de quem RECEBE.
--
-- O sharer decide o que sai (grant_rules). Isto e o contrario: quem recebe pode
-- dizer "esse app continua chegando no meu feed, mas nao me notifica". A entrega
-- (event_deliveries) e criada normalmente; so o push e suprimido.
--
-- Linha presente = app silenciado naquele grant. Ausente = notifica (padrao).

create table recipient_app_mutes (
    id           uuid         primary key,
    grant_id     uuid         not null references grants (id) on delete cascade,
    recipient_id uuid         not null references users (id) on delete cascade,
    package_name varchar(255) not null,
    created_at   timestamptz  not null default now(),
    unique (grant_id, package_name)
);

create index ix_recipient_app_mutes_grant on recipient_app_mutes (grant_id);

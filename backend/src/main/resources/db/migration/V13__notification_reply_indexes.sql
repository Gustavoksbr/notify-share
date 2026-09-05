-- Índices adicionais para otimizar a busca de localização de eventos.
-- Estes índices melhoram a performance das queries countBeforeEvent e feedAroundEvent.

-- Já existe: create index ix_messages_linked_event on messages (linked_event_id);
-- Criado em V8__message_reply_edit.sql

-- Índice composto para acelerar queries de feed com filtros
-- Suporta as queries que ordenam por occurred_at desc e filtram por recipient + package/type
create index if not exists ix_event_deliveries_recipient_occurred
    on event_deliveries (recipient_id, event_id);

-- Índice para acelerar busca de eventos por occurred_at (usado em feedAroundEvent)
create index if not exists ix_events_occurred_at
    on events (occurred_at desc);

-- Índice composto para acelerar filtros de package + type
create index if not exists ix_events_package_type
    on events (package_name, event_type, occurred_at desc);

-- Comentário: A query countBeforeEvent se beneficia dos índices existentes:
--   - event_deliveries(recipient_id, event_id)
--   - events(id) [PK]
--   - events(occurred_at)
-- 
-- Para feeds com milhões de eventos, considere:
--   - Particionamento por occurred_at (mensal/anual)
--   - Materializar views para feeds frequentemente acessados
--   - Cache em Redis para páginas recentes

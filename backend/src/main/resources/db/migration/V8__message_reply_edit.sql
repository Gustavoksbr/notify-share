-- Conversa privada mais rica: responder, editar, apagar e "responder uma
-- notificacao" (a mensagem aponta para o evento que a originou).

alter table messages
    -- resposta a outra mensagem da mesma conversa
    add column reply_to_id     uuid references messages (id) on delete set null,
    -- "isto e sobre aquela notificacao": o evento compartilhado. Fica null
    -- quando o evento e apagado pela retencao.
    add column linked_event_id uuid references events (id) on delete set null,
    -- editada em; a UI mostra "(editado)"
    add column edited_at       timestamptz,
    -- apagada em; soft delete: a linha fica para "esta mensagem foi apagada"
    -- e para as respostas continuarem resolvendo
    add column deleted_at      timestamptz;

create index ix_messages_reply_to on messages (reply_to_id);
create index ix_messages_linked_event on messages (linked_event_id);

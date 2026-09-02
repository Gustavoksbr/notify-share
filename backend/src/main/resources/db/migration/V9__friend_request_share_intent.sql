-- Ao pedir amizade, o solicitante pode ja sinalizar que quer compartilhar
-- e/ou receber notificacoes. Quando o pedido de amizade e aceito, o backend
-- cria os grants pendentes correspondentes (na direcao certa, iniciados pelo
-- solicitante). Sem isso, o novo amigo tinha que ir na aba Compartilhar e
-- refazer o pedido manualmente.

alter table friendships
    -- o solicitante quer compartilhar as notificacoes DELE com o destinatario
    add column also_offer_share   boolean not null default false,
    -- o solicitante quer receber as notificacoes do destinatario
    add column also_request_share boolean not null default false;

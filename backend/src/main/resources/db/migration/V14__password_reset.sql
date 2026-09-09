-- Recuperacao de senha por codigo de 6 digitos enviado por e-mail.
--
-- O e-mail passa a ser USADO (antes so servia de UNIQUE, ver ADR-001). Nao ha
-- verificacao de e-mail no cadastro; o fluxo de reset e o unico ponto em que
-- provar posse do endereco importa, e ele se prova sozinho: so quem abre a
-- caixa recebe o codigo.
--
-- O codigo em claro nunca fica guardado: so o SHA-256 dele em hex, igual aos
-- refresh tokens. A consulta e por (user_id, code_hash) — o mobile reenvia o
-- e-mail no reset, entao nao dependemos do code_hash ser unico global (dois
-- usuarios podem sortear "123456" ao mesmo tempo).

create table password_reset_tokens (
    id          uuid        primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    code_hash   varchar(64) not null,
    created_at  timestamptz not null default now(),
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    -- tentativas de codigo errado NESTE token; passou do teto, o token morre
    attempts    smallint    not null default 0
);

create index ix_password_reset_user on password_reset_tokens (user_id);
create index ix_password_reset_expires on password_reset_tokens (expires_at);

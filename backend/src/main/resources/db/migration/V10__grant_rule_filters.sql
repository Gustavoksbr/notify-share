-- Filtro por texto no titulo/corpo (ex.: nome de canal do YouTube, que nao tem
-- objeto Person). Guardado como termos separados por quebra de linha; vazio/NULL
-- = sem filtro. E a protecao embutida contra codigos de verificacao (OTP), com
-- opt-out por regra.
alter table grant_rules add column text_filters text;
alter table grant_rules add column allow_codes boolean not null default false;

-- Consentimento LGPD no cadastro. NULL nas contas que ja existiam — o app
-- pede o aceite no proximo login e grava aqui.
alter table users add column privacy_accepted_at timestamptz;

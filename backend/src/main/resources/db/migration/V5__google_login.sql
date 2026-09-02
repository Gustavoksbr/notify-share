-- Login com Google.
--
-- password_hash deixa de ser obrigatorio: uma conta pode nascer so pelo Google,
-- sem senha. google_sub e o identificador estavel da conta Google (nao muda
-- nem quando a pessoa troca o e-mail).

alter table users alter column password_hash drop not null;

alter table users add column google_sub varchar(255);

-- NULLs nao conflitam num unique do Postgres, entao contas sem Google convivem.
create unique index ux_users_google_sub on users (google_sub);

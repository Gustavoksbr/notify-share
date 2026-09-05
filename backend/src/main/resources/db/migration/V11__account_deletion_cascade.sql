-- Exclusao de conta: as duas FKs para users(id) que ainda nao cascateavam.
-- Com elas, "delete from users where id = ?" leva tudo junto.
alter table grants drop constraint grants_initiated_by_fkey;
alter table grants add constraint grants_initiated_by_fkey
    foreign key (initiated_by) references users (id) on delete cascade;

alter table grant_audit drop constraint grant_audit_actor_id_fkey;
alter table grant_audit add constraint grant_audit_actor_id_fkey
    foreign key (actor_id) references users (id) on delete cascade;

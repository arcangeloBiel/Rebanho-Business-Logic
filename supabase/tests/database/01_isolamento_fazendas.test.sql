-- Testes do PRD da Fase 2 (fatia 1): isolamento entre fazendas e permissões por papel.
-- Rodam com `supabase test db`. Cada asserção cita o requisito que comprova.
--
-- Usuários:  A = dono da Fazenda A,  B = dono da Fazenda B,  F = funcionário da Fazenda A
begin;
create extension if not exists pgtap with schema extensions;
select plan(29);

-- ---------------------------------------------------------------------
-- Preparação (como postgres)
-- ---------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
    ('00000000-0000-0000-0000-00000000000a', 'dono.a@teste.com', '{"nome": "Ana"}'),
    ('00000000-0000-0000-0000-00000000000b', 'dono.b@teste.com', '{"nome": "Bruno"}'),
    ('00000000-0000-0000-0000-00000000000f', 'funcionario@teste.com', '{"nome": "Fábio"}');

select is(
    (select count(*)::int from pg_tables where schemaname = 'public' and not rowsecurity),
    0,
    'RNF-01: todas as tabelas do schema public têm RLS ativa'
);

select is(
    (select count(*)::int from public.perfis),
    3,
    'Perfil criado automaticamente para cada conta nova'
);

-- ---------------------------------------------------------------------
-- A e B criam suas fazendas
-- ---------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-00000000000a", "role": "authenticated"}';

select lives_ok(
    $$ select public.criar_fazenda('aaaaaaaa-0000-0000-0000-000000000001', 'Fazenda A', 'Uberaba', 'mg') $$,
    'RF-07: A cria a Fazenda A'
);
select is(
    (select papel::text from public.membros
     where fazenda_id = 'aaaaaaaa-0000-0000-0000-000000000001'
       and usuario_id = '00000000-0000-0000-0000-00000000000a'),
    'DONO',
    'RF-07/CA-05.1: quem cria a fazenda vira DONO'
);
select is(
    (select uf from public.fazendas where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    'MG',
    'UF é guardada em maiúsculas'
);
select throws_ok(
    $$ select public.criar_fazenda(null, '   ') $$,
    '22023', null,
    'CA-05.3: nome em branco é recusado'
);
select lives_ok(
    $$ insert into public.animais (id, fazenda_id, brinco, sexo, origem, data_nascimento)
       values ('aaaaaaaa-0000-0000-0000-0000000000a1', 'aaaaaaaa-0000-0000-0000-000000000001',
               'A001', 'FEMEA', 'NASCIDO_NA_FAZENDA', '2024-03-10') $$,
    'RF-10: A cadastra animal na própria fazenda'
);
select throws_ok(
    $$ insert into public.animais (fazenda_id, brinco, sexo, origem)
       values ('aaaaaaaa-0000-0000-0000-000000000001', 'A001', 'MACHO', 'COMPRADO') $$,
    '23505', null,
    'Brinco duplicado na mesma fazenda é recusado pelo banco'
);

reset role;
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-00000000000b", "role": "authenticated"}';

select lives_ok(
    $$ select public.criar_fazenda('bbbbbbbb-0000-0000-0000-000000000001', 'Fazenda B') $$,
    'RF-07: B cria a Fazenda B'
);
select lives_ok(
    $$ insert into public.animais (id, fazenda_id, brinco, sexo, origem, data_nascimento)
       values ('bbbbbbbb-0000-0000-0000-0000000000b1', 'bbbbbbbb-0000-0000-0000-000000000001',
               'A001', 'MACHO', 'NASCIDO_NA_FAZENDA', '2025-01-05') $$,
    'O mesmo brinco pode existir em outra fazenda'
);

-- ---------------------------------------------------------------------
-- CA-07.1 e CA-07.2: B não lê nem altera nada de A
-- ---------------------------------------------------------------------
select is(
    (select count(*)::int from public.animais where fazenda_id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    0,
    'CA-07.1: B não lê os animais da Fazenda A'
);
select is(
    (select count(*)::int from public.fazendas),
    1,
    'CA-07.1: B só enxerga a própria fazenda'
);
select is(
    (select count(*)::int from public.perfis),
    1,
    'CA-07.1: B só enxerga o próprio perfil'
);
select throws_ok(
    $$ insert into public.animais (fazenda_id, brinco, sexo, origem)
       values ('aaaaaaaa-0000-0000-0000-000000000001', 'INVASOR', 'MACHO', 'COMPRADO') $$,
    '42501', null,
    'CA-07.2: B não insere animal na Fazenda A'
);
select throws_ok(
    $$ insert into public.pesagens (fazenda_id, animal_id, data, peso_kg)
       values ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-0000000000a1', '2026-11-01', 300) $$,
    '23503', null,
    'CA-07.2: B não liga uma pesagem da própria fazenda a um animal da Fazenda A'
);
select throws_ok(
    $$ insert into public.membros (fazenda_id, usuario_id, papel)
       values ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000b', 'DONO') $$,
    '42501', null,
    'CA-07.2: B não se adiciona como membro da Fazenda A'
);
update public.animais set nome = 'invadido' where id = 'aaaaaaaa-0000-0000-0000-0000000000a1';

-- ---------------------------------------------------------------------
-- A adiciona F como FUNCIONARIO
-- ---------------------------------------------------------------------
reset role;
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-00000000000a", "role": "authenticated"}';

select is(
    (select nome from public.animais where id = 'aaaaaaaa-0000-0000-0000-0000000000a1'),
    null::text,
    'CA-07.2: a tentativa de B de alterar o animal de A não teve efeito'
);
select lives_ok(
    $$ insert into public.membros (fazenda_id, usuario_id, papel)
       values ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000f', 'FUNCIONARIO') $$,
    'RN-05: o DONO adiciona um funcionário'
);

-- ---------------------------------------------------------------------
-- CA-07.3: o funcionário trabalha, mas não exclui
-- ---------------------------------------------------------------------
reset role;
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-00000000000f", "role": "authenticated"}';

select is(
    (select count(*)::int from public.animais),
    1,
    'RN-05: o funcionário lê o rebanho da fazenda em que trabalha'
);
select is(
    (select count(*)::int from public.perfis),
    2,
    'O funcionário vê o próprio perfil e o do dono da fazenda'
);
select lives_ok(
    $$ insert into public.pesagens (fazenda_id, animal_id, data, peso_kg)
       values ('aaaaaaaa-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-0000000000a1', '2026-11-01', 320.5) $$,
    'RN-05: o funcionário registra uma pesagem'
);
select throws_ok(
    $$ update public.animais set deleted_at = now() where id = 'aaaaaaaa-0000-0000-0000-0000000000a1' $$,
    '42501', null,
    'CA-07.3: o funcionário não marca um animal como excluído'
);
select throws_ok(
    $$ insert into public.lotes (fazenda_id, nome, deleted_at)
       values ('aaaaaaaa-0000-0000-0000-000000000001', 'Lote apagado', now()) $$,
    '42501', null,
    'CA-07.3: o funcionário não insere registro já excluído'
);
update public.fazendas set nome = 'Renomeada pelo funcionário' where id = 'aaaaaaaa-0000-0000-0000-000000000001';

-- ---------------------------------------------------------------------
-- O DONO exclui; ninguém apaga de fato; a fazenda nunca fica sem dono
-- ---------------------------------------------------------------------
reset role;
set local role authenticated;
set local request.jwt.claims to '{"sub": "00000000-0000-0000-0000-00000000000a", "role": "authenticated"}';

select is(
    (select nome from public.fazendas where id = 'aaaaaaaa-0000-0000-0000-000000000001'),
    'Fazenda A',
    'RN-05: o funcionário não altera os dados da fazenda'
);
select lives_ok(
    $$ update public.animais set deleted_at = now() where id = 'aaaaaaaa-0000-0000-0000-0000000000a1' $$,
    'CA-07.3: o DONO marca o animal como excluído'
);
delete from public.animais where id = 'aaaaaaaa-0000-0000-0000-0000000000a1';
select throws_ok(
    $$ update public.membros set deleted_at = now()
       where fazenda_id = 'aaaaaaaa-0000-0000-0000-000000000001'
         and usuario_id = '00000000-0000-0000-0000-00000000000a' $$,
    '23514', null,
    'RN-04: o último DONO não pode sair da fazenda'
);

reset role;
select is(
    (select count(*)::int from public.animais where id = 'aaaaaaaa-0000-0000-0000-0000000000a1'),
    1,
    'RN-06: DELETE não apaga o registro de fato'
);

-- ---------------------------------------------------------------------
-- CA-07.4: sem login, nada
-- ---------------------------------------------------------------------
set local role anon;
set local request.jwt.claims to '{"role": "anon"}';

select throws_ok(
    $$ select count(*) from public.animais $$,
    '42501', null,
    'CA-07.4: usuário não autenticado não lê nenhuma tabela'
);
select throws_ok(
    $$ select public.criar_fazenda(null, 'Fazenda anônima') $$,
    '42501', null,
    'CA-07.4: usuário não autenticado não cria fazenda'
);

reset role;
select * from finish();
rollback;

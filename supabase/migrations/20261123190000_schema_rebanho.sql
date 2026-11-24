-- =====================================================================
-- Rebanho Mais — Fase 2, fatia 1: banco seguro
-- PRD: HU-07, RF-07, RF-10, RF-11, RN-04, RN-05, RN-06, RNF-01
--
-- Regras de acesso (RN-05):
--   DONO        lê, cria, altera e marca como excluído; altera a fazenda e os membros
--   FUNCIONARIO lê, cria e altera; não exclui
--   TECNICO     lê, cria e altera; não exclui
--   sem vínculo nada
--   DELETE de verdade: ninguém (exclusão é deleted_at, RN-06)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Tipos
-- ---------------------------------------------------------------------
create type public.papel as enum ('DONO', 'FUNCIONARIO', 'TECNICO');
create type public.sexo as enum ('FEMEA', 'MACHO');
create type public.aptidao as enum ('CORTE', 'LEITE', 'MISTA');
create type public.origem as enum ('NASCIDO_NA_FAZENDA', 'COMPRADO');
create type public.status_animal as enum ('ATIVO', 'VENDIDO', 'MORTO', 'TRANSFERIDO');
create type public.tipo_movimentacao as enum ('NASCIMENTO', 'COMPRA', 'VENDA', 'MORTE', 'TRANSFERENCIA');

-- ---------------------------------------------------------------------
-- Tabelas
-- Todas as tabelas do rebanho têm fazenda_id, created_at, updated_at e
-- deleted_at. O id é um UUID gerado no aparelho (sincronização na Fase 4).
-- As chaves estrangeiras compostas (fazenda_id, x_id) impedem ligar um
-- registro a um animal, lote ou protocolo de outra fazenda.
-- ---------------------------------------------------------------------
create table public.perfis (
    id uuid primary key references auth.users (id) on delete cascade,
    nome text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.fazendas (
    id uuid primary key default gen_random_uuid(),
    nome text not null check (length(btrim(nome)) > 0),
    municipio text,
    uf text check (uf is null or uf ~ '^[A-Z]{2}$'),
    codigo_propriedade text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table public.membros (
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    usuario_id uuid not null references auth.users (id) on delete cascade,
    papel public.papel not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    primary key (fazenda_id, usuario_id)
);
create index membros_usuario_idx on public.membros (usuario_id);

create table public.lotes (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    nome text not null check (length(btrim(nome)) > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (fazenda_id, id)
);

create table public.animais (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    brinco text not null check (brinco ~ '^[A-Z0-9-]{1,20}$'),
    sexo public.sexo not null,
    origem public.origem not null,
    data_nascimento date,
    nome text,
    raca text,
    aptidao public.aptidao not null default 'MISTA',
    mae_id uuid,
    pai_id uuid,
    lote_id uuid,
    reprodutor boolean not null default false,
    status public.status_animal not null default 'ATIVO',
    observacao text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (fazenda_id, id),
    unique (fazenda_id, brinco),
    check (mae_id is null or mae_id <> id),
    check (pai_id is null or pai_id <> id)
);
alter table public.animais
    add constraint animais_mae_fk foreign key (fazenda_id, mae_id) references public.animais (fazenda_id, id),
    add constraint animais_pai_fk foreign key (fazenda_id, pai_id) references public.animais (fazenda_id, id),
    add constraint animais_lote_fk foreign key (fazenda_id, lote_id) references public.lotes (fazenda_id, id);
create index animais_mae_idx on public.animais (mae_id);

create table public.pesagens (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    animal_id uuid not null,
    data date not null,
    peso_kg numeric(7, 2) not null check (peso_kg > 0 and peso_kg <= 2000),
    observacao text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    foreign key (fazenda_id, animal_id) references public.animais (fazenda_id, id)
);
create index pesagens_animal_idx on public.pesagens (animal_id);

create table public.protocolos_sanitarios (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    nome text not null check (length(btrim(nome)) > 0),
    doses integer not null default 1 check (doses >= 1),
    intervalo_entre_doses_dias integer check (intervalo_entre_doses_dias > 0),
    reforco_dias integer check (reforco_dias > 0),
    sexo_alvo public.sexo,
    idade_minima_meses integer check (idade_minima_meses >= 0),
    idade_maxima_meses integer check (idade_maxima_meses >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (fazenda_id, id),
    check (doses = 1 or intervalo_entre_doses_dias is not null),
    check (idade_minima_meses is null or idade_maxima_meses is null or idade_minima_meses <= idade_maxima_meses)
);

create table public.aplicacoes_vacina (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    animal_id uuid not null,
    protocolo_id uuid,
    nome_vacina text not null check (length(btrim(nome_vacina)) > 0),
    data date not null,
    dose integer not null default 1 check (dose >= 1),
    lote_produto text,
    observacao text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    foreign key (fazenda_id, animal_id) references public.animais (fazenda_id, id),
    foreign key (fazenda_id, protocolo_id) references public.protocolos_sanitarios (fazenda_id, id)
);
create index aplicacoes_animal_idx on public.aplicacoes_vacina (animal_id);

create table public.movimentacoes (
    id uuid primary key default gen_random_uuid(),
    fazenda_id uuid not null references public.fazendas (id) on delete cascade,
    animal_id uuid not null,
    tipo public.tipo_movimentacao not null,
    data date not null,
    contraparte text,
    valor numeric(12, 2) check (valor is null or valor >= 0),
    observacao text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    foreign key (fazenda_id, animal_id) references public.animais (fazenda_id, id)
);
create index movimentacoes_animal_idx on public.movimentacoes (animal_id);

-- Índices para a sincronização incremental (Fase 4): "o que mudou desde X nesta fazenda".
create index lotes_sync_idx on public.lotes (fazenda_id, updated_at);
create index animais_sync_idx on public.animais (fazenda_id, updated_at);
create index pesagens_sync_idx on public.pesagens (fazenda_id, updated_at);
create index protocolos_sync_idx on public.protocolos_sanitarios (fazenda_id, updated_at);
create index aplicacoes_sync_idx on public.aplicacoes_vacina (fazenda_id, updated_at);
create index movimentacoes_sync_idx on public.movimentacoes (fazenda_id, updated_at);

-- ---------------------------------------------------------------------
-- Funções de acesso (security definer para ler membros sem recursão de RLS)
-- ---------------------------------------------------------------------
create function public.papel_na_fazenda(p_fazenda_id uuid)
returns public.papel
language sql
stable
security definer
set search_path = ''
as $$
    select m.papel
    from public.membros m
    where m.fazenda_id = p_fazenda_id
      and m.usuario_id = (select auth.uid())
      and m.deleted_at is null
$$;

create function public.e_membro(p_fazenda_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select public.papel_na_fazenda(p_fazenda_id) is not null
$$;

create function public.e_dono(p_fazenda_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(public.papel_na_fazenda(p_fazenda_id) = 'DONO', false)
$$;

create function public.compartilha_fazenda(p_usuario_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.membros eu
        join public.membros outro on outro.fazenda_id = eu.fazenda_id
        where eu.usuario_id = (select auth.uid())
          and outro.usuario_id = p_usuario_id
          and eu.deleted_at is null
          and outro.deleted_at is null
    )
$$;

-- ---------------------------------------------------------------------
-- Triggers
-- ---------------------------------------------------------------------

-- updated_at sempre preenchido pelo servidor (base da sincronização incremental).
create function public.tocar_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

-- RN-05/RN-06: só o DONO marca ou desmarca deleted_at; fazenda_id nunca muda.
-- Chamadas sem usuário (postgres, service_role, migrations) não são bloqueadas.
create function public.proteger_registro()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    -- Na tabela fazendas o "dono" do registro é o próprio id; nas outras, fazenda_id.
    v_fazenda_antiga uuid := coalesce(to_jsonb(old) ->> 'fazenda_id', to_jsonb(old) ->> 'id')::uuid;
    v_fazenda_nova uuid := coalesce(to_jsonb(new) ->> 'fazenda_id', to_jsonb(new) ->> 'id')::uuid;
begin
    if (select auth.uid()) is null then
        return new;
    end if;
    if v_fazenda_nova is distinct from v_fazenda_antiga then
        raise exception 'Não é permitido mudar o registro de fazenda.' using errcode = '42501';
    end if;
    if new.deleted_at is distinct from old.deleted_at and not public.e_dono(v_fazenda_nova) then
        raise exception 'Somente o DONO pode excluir ou restaurar registros.' using errcode = '42501';
    end if;
    return new;
end;
$$;

-- Insert já marcado como excluído também é exclusão.
create function public.proteger_insercao_excluida()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if (select auth.uid()) is not null and new.deleted_at is not null and not public.e_dono(new.fazenda_id) then
        raise exception 'Somente o DONO pode excluir registros.' using errcode = '42501';
    end if;
    return new;
end;
$$;

-- RN-04: toda fazenda mantém pelo menos um DONO ativo.
create function public.garantir_um_dono()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if not exists (
        select 1 from public.membros
        where fazenda_id = old.fazenda_id
          and papel = 'DONO'
          and deleted_at is null
    ) then
        raise exception 'A fazenda precisa ter pelo menos um DONO.' using errcode = '23514';
    end if;
    return null;
end;
$$;

-- Cria o perfil quando a conta é criada no Auth.
create function public.criar_perfil_do_usuario()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.perfis (id, nome)
    values (new.id, nullif(btrim(new.raw_user_meta_data ->> 'nome'), ''))
    on conflict (id) do nothing;
    return new;
end;
$$;

create trigger ao_criar_usuario
    after insert on auth.users
    for each row execute function public.criar_perfil_do_usuario();

create trigger tocar_updated_at before update on public.perfis
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.fazendas
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.membros
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.lotes
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.animais
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.pesagens
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.protocolos_sanitarios
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.aplicacoes_vacina
    for each row execute function public.tocar_updated_at();
create trigger tocar_updated_at before update on public.movimentacoes
    for each row execute function public.tocar_updated_at();

create trigger proteger_registro before update on public.fazendas
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.membros
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.lotes
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.animais
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.pesagens
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.protocolos_sanitarios
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.aplicacoes_vacina
    for each row execute function public.proteger_registro();
create trigger proteger_registro before update on public.movimentacoes
    for each row execute function public.proteger_registro();

create trigger proteger_insercao_excluida before insert on public.lotes
    for each row execute function public.proteger_insercao_excluida();
create trigger proteger_insercao_excluida before insert on public.animais
    for each row execute function public.proteger_insercao_excluida();
create trigger proteger_insercao_excluida before insert on public.pesagens
    for each row execute function public.proteger_insercao_excluida();
create trigger proteger_insercao_excluida before insert on public.protocolos_sanitarios
    for each row execute function public.proteger_insercao_excluida();
create trigger proteger_insercao_excluida before insert on public.aplicacoes_vacina
    for each row execute function public.proteger_insercao_excluida();
create trigger proteger_insercao_excluida before insert on public.movimentacoes
    for each row execute function public.proteger_insercao_excluida();

create trigger garantir_um_dono after update on public.membros
    for each row execute function public.garantir_um_dono();

-- ---------------------------------------------------------------------
-- RF-07: criar a fazenda e o vínculo DONO numa única operação
-- ---------------------------------------------------------------------
create function public.criar_fazenda(
    p_id uuid,
    p_nome text,
    p_municipio text default null,
    p_uf text default null,
    p_codigo_propriedade text default null
)
returns public.fazendas
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_usuario uuid := (select auth.uid());
    v_fazenda public.fazendas;
begin
    if v_usuario is null then
        raise exception 'É preciso estar logado para criar uma fazenda.' using errcode = '42501';
    end if;
    if p_nome is null or length(btrim(p_nome)) = 0 then
        raise exception 'O nome da fazenda é obrigatório.' using errcode = '22023';
    end if;

    insert into public.fazendas (id, nome, municipio, uf, codigo_propriedade)
    values (
        coalesce(p_id, gen_random_uuid()),
        btrim(p_nome),
        nullif(btrim(p_municipio), ''),
        upper(nullif(btrim(p_uf), '')),
        nullif(btrim(p_codigo_propriedade), '')
    )
    returning * into v_fazenda;

    insert into public.membros (fazenda_id, usuario_id, papel)
    values (v_fazenda.id, v_usuario, 'DONO');

    return v_fazenda;
end;
$$;

-- ---------------------------------------------------------------------
-- Permissões: nada para anon (RNF-01); funções só para quem está logado
-- ---------------------------------------------------------------------
revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;
alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke all on sequences from anon;

revoke execute on function public.papel_na_fazenda(uuid) from public, anon;
revoke execute on function public.e_membro(uuid) from public, anon;
revoke execute on function public.e_dono(uuid) from public, anon;
revoke execute on function public.compartilha_fazenda(uuid) from public, anon;
revoke execute on function public.criar_fazenda(uuid, text, text, text, text) from public, anon;
revoke execute on function public.criar_perfil_do_usuario() from public, anon, authenticated;
grant execute on function public.papel_na_fazenda(uuid) to authenticated;
grant execute on function public.e_membro(uuid) to authenticated;
grant execute on function public.e_dono(uuid) to authenticated;
grant execute on function public.compartilha_fazenda(uuid) to authenticated;
grant execute on function public.criar_fazenda(uuid, text, text, text, text) to authenticated;

-- ---------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------
alter table public.perfis enable row level security;
alter table public.fazendas enable row level security;
alter table public.membros enable row level security;
alter table public.lotes enable row level security;
alter table public.animais enable row level security;
alter table public.pesagens enable row level security;
alter table public.protocolos_sanitarios enable row level security;
alter table public.aplicacoes_vacina enable row level security;
alter table public.movimentacoes enable row level security;

-- perfis: o próprio usuário, e quem divide alguma fazenda com ele (para mostrar o nome)
create policy perfis_ler on public.perfis for select to authenticated
    using (id = (select auth.uid()) or public.compartilha_fazenda(id));
create policy perfis_criar on public.perfis for insert to authenticated
    with check (id = (select auth.uid()));
create policy perfis_alterar on public.perfis for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- fazendas: membros leem; só o DONO altera; criação só por criar_fazenda()
create policy fazendas_ler on public.fazendas for select to authenticated
    using (public.e_membro(id));
create policy fazendas_alterar on public.fazendas for update to authenticated
    using (public.e_dono(id)) with check (public.e_dono(id));

-- membros: membros veem os colegas; só o DONO adiciona ou altera
create policy membros_ler on public.membros for select to authenticated
    using (public.e_membro(fazenda_id));
create policy membros_criar on public.membros for insert to authenticated
    with check (public.e_dono(fazenda_id));
create policy membros_alterar on public.membros for update to authenticated
    using (public.e_dono(fazenda_id)) with check (public.e_dono(fazenda_id));

-- tabelas do rebanho: qualquer membro lê, cria e altera; exclusão controlada por trigger
create policy lotes_ler on public.lotes for select to authenticated using (public.e_membro(fazenda_id));
create policy lotes_criar on public.lotes for insert to authenticated with check (public.e_membro(fazenda_id));
create policy lotes_alterar on public.lotes for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

create policy animais_ler on public.animais for select to authenticated using (public.e_membro(fazenda_id));
create policy animais_criar on public.animais for insert to authenticated with check (public.e_membro(fazenda_id));
create policy animais_alterar on public.animais for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

create policy pesagens_ler on public.pesagens for select to authenticated using (public.e_membro(fazenda_id));
create policy pesagens_criar on public.pesagens for insert to authenticated with check (public.e_membro(fazenda_id));
create policy pesagens_alterar on public.pesagens for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

create policy protocolos_ler on public.protocolos_sanitarios for select to authenticated using (public.e_membro(fazenda_id));
create policy protocolos_criar on public.protocolos_sanitarios for insert to authenticated with check (public.e_membro(fazenda_id));
create policy protocolos_alterar on public.protocolos_sanitarios for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

create policy aplicacoes_ler on public.aplicacoes_vacina for select to authenticated using (public.e_membro(fazenda_id));
create policy aplicacoes_criar on public.aplicacoes_vacina for insert to authenticated with check (public.e_membro(fazenda_id));
create policy aplicacoes_alterar on public.aplicacoes_vacina for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

create policy movimentacoes_ler on public.movimentacoes for select to authenticated using (public.e_membro(fazenda_id));
create policy movimentacoes_criar on public.movimentacoes for insert to authenticated with check (public.e_membro(fazenda_id));
create policy movimentacoes_alterar on public.movimentacoes for update to authenticated
    using (public.e_membro(fazenda_id)) with check (public.e_membro(fazenda_id));

-- Sem política de DELETE em nenhuma tabela: apagar de fato não é permitido (RN-06).

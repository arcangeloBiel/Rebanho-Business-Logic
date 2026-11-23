-- =====================================================================
-- Rebanho Mais — Fase 2, fatia 1: ajustes apontados pelos advisors do Supabase
--
-- 1. Segurança (lint 0029): as funções auxiliares das regras de acesso saem
--    do schema public (exposto pela API REST) e vão para o schema "privado".
--    As políticas de RLS guardam a função pelo OID, então continuam valendo.
--    criar_fazenda() continua em public: é a função que o app chama.
-- 2. Desempenho (lint 0001): índices que cobrem as chaves estrangeiras
--    compostas (fazenda_id, x_id).
-- =====================================================================

create schema if not exists privado;
revoke all on schema privado from public, anon;
grant usage on schema privado to authenticated;

alter function public.papel_na_fazenda(uuid) set schema privado;
alter function public.e_membro(uuid) set schema privado;
alter function public.e_dono(uuid) set schema privado;
alter function public.compartilha_fazenda(uuid) set schema privado;

-- Corpos que chamavam public.papel_na_fazenda / public.e_dono pelo nome.
create or replace function privado.e_membro(p_fazenda_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select privado.papel_na_fazenda(p_fazenda_id) is not null
$$;

create or replace function privado.e_dono(p_fazenda_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce(privado.papel_na_fazenda(p_fazenda_id) = 'DONO', false)
$$;

create or replace function public.proteger_registro()
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
    if new.deleted_at is distinct from old.deleted_at and not privado.e_dono(v_fazenda_nova) then
        raise exception 'Somente o DONO pode excluir ou restaurar registros.' using errcode = '42501';
    end if;
    return new;
end;
$$;

create or replace function public.proteger_insercao_excluida()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if (select auth.uid()) is not null and new.deleted_at is not null and not privado.e_dono(new.fazenda_id) then
        raise exception 'Somente o DONO pode excluir registros.' using errcode = '42501';
    end if;
    return new;
end;
$$;

-- Funções de trigger não precisam ser chamadas pela API.
revoke execute on function public.tocar_updated_at() from public, anon, authenticated;
revoke execute on function public.proteger_registro() from public, anon, authenticated;
revoke execute on function public.proteger_insercao_excluida() from public, anon, authenticated;
revoke execute on function public.garantir_um_dono() from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- Índices que cobrem as chaves estrangeiras compostas
-- ---------------------------------------------------------------------
drop index if exists public.animais_mae_idx;
drop index if exists public.pesagens_animal_idx;
drop index if exists public.aplicacoes_animal_idx;
drop index if exists public.movimentacoes_animal_idx;

create index animais_mae_idx on public.animais (fazenda_id, mae_id);
create index animais_pai_idx on public.animais (fazenda_id, pai_id);
create index animais_lote_idx on public.animais (fazenda_id, lote_id);
create index pesagens_animal_idx on public.pesagens (fazenda_id, animal_id);
create index aplicacoes_animal_idx on public.aplicacoes_vacina (fazenda_id, animal_id);
create index aplicacoes_protocolo_idx on public.aplicacoes_vacina (fazenda_id, protocolo_id);
create index movimentacoes_animal_idx on public.movimentacoes (fazenda_id, animal_id);

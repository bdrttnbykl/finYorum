create table crypto_market_snapshots (
    id bigserial primary key,
    symbol varchar(32) not null,
    coin_id varchar(255) not null,
    name varchar(255) not null,
    market_cap_rank integer,
    current_price numeric(38, 12) not null,
    price_change_24h numeric(38, 12) not null,
    price_change_percentage_24h numeric(38, 12) not null,
    low_24h numeric(38, 12) not null,
    high_24h numeric(38, 12) not null,
    market_cap numeric(38, 2) not null,
    fully_diluted_valuation numeric(38, 2) not null,
    total_volume numeric(38, 2) not null,
    circulating_supply numeric(38, 2) not null,
    total_supply numeric(38, 2) not null,
    max_supply numeric(38, 2) not null,
    chart_json text not null,
    volatility numeric(18, 8) not null,
    sharpe_ratio numeric(18, 8) not null,
    risk_level varchar(32) not null,
    fetched_at timestamp with time zone not null
);

create index idx_crypto_market_snapshots_symbol_fetched_at
    on crypto_market_snapshots(symbol, fetched_at desc);

alter table ai_analyses add column snapshot_id bigint;
alter table ai_analyses add column provider varchar(32);
alter table ai_analyses add column model varchar(128);

create index idx_ai_analyses_snapshot_id_created_at
    on ai_analyses(snapshot_id, created_at desc);

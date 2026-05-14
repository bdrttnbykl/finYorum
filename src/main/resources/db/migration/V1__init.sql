create table user_accounts (
    id bigserial primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    risk_profile varchar(32) not null,
    created_at timestamp with time zone not null
);

create table portfolio_assets (
    id bigserial primary key,
    user_id bigint not null,
    symbol varchar(32) not null,
    quantity numeric(19, 6) not null,
    average_price numeric(19, 6) not null
);

create table ai_analyses (
    id bigserial primary key,
    symbol varchar(32) not null,
    recommendation varchar(32) not null,
    summary varchar(4000) not null,
    created_at timestamp with time zone not null
);

create index idx_portfolio_assets_user_id on portfolio_assets(user_id);
create index idx_ai_analyses_symbol_created_at on ai_analyses(symbol, created_at desc);

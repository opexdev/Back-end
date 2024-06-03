CREATE TABLE IF NOT EXISTS currency
(
    symbol            VARCHAR(25) PRIMARY KEY,
    name              VARCHAR(25),
    precision         DECIMAL NOT NULL,
    title             VARCHAR(25),
    alias             VARCHAR(25),
    max_deposit       DECIMAL,
    min_deposit       DECIMAL,
    min_Withdraw      DECIMAL,
    max_withdraw      DECIMAL,
    icon              TEXT,
    last_update_date  TIMESTAMP,
    create_date       TIMESTAMP,
    is_transitive     BOOLEAN NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    sign              VARCHAR(25),
    description       TEXT,
    short_description TEXT
);


CREATE TABLE IF NOT EXISTS  new_currency (
    id SERIAL              PRIMARY KEY,
    symbol                 VARCHAR(255) UNIQUE NOT NULL,
    uuid                   VARCHAR(255) UNIQUE NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    precision              NUMERIC,
    title                  VARCHAR(255),
    alias                  VARCHAR(255),
    icon                   VARCHAR(255),
    is_transitive          BOOLEAN DEFAULT FALSE,
    is_active              BOOLEAN DEFAULT TRUE,
    sign                   VARCHAR(255),
    description            TEXT,
    short_description      VARCHAR(255),
    withdraw_is_enable     BOOLEAN DEFAULT TRUE,
    deposit_is_enable      BOOLEAN DEFAULT TRUE,
    withdraw_fee           NUMERIC,
    external_url           VARCHAR(255),
    is_crypto_currency     BOOLEAN DEFAULT FALSE
    );

ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS title VARCHAR(25);
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS alias VARCHAR(25);
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS max_deposit DECIMAL;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS min_deposit DECIMAL;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS min_Withdraw DECIMAL;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS max_withdraw DECIMAL;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS icon TEXT;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS last_update_date TIMESTAMP;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS create_date TIMESTAMP;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS is_transitive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS sign VARCHAR(25);
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE currency
    ADD COLUMN IF NOT EXISTS short_description TEXT;


CREATE TABLE IF NOT EXISTS wallet_owner
(
    id               SERIAL PRIMARY KEY,
    uuid             VARCHAR(36) NOT NULL UNIQUE,
    title            VARCHAR(70) NOT NULL,
    level            VARCHAR(10) NOT NULL,
    trade_allowed    BOOLEAN     NOT NULL DEFAULT TRUE,
    withdraw_allowed BOOLEAN     NOT NULL DEFAULT TRUE,
    deposit_allowed  BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS wallet
(
    id          SERIAL PRIMARY KEY,
    owner       INTEGER     NOT NULL REFERENCES wallet_owner (id),
    wallet_type VARCHAR(10) NOT NULL,
    currency    INTEGER NOT NULL REFERENCES new_currency (id),
    balance     DECIMAL     NOT NULL,
    UNIQUE (owner, wallet_type, currency)
);



ALTER TABLE wallet
    ADD COLUMN IF NOT EXISTS version INTEGER;

CREATE TABLE IF NOT EXISTS transaction
(
    id               SERIAL PRIMARY KEY,
    source_wallet    INTEGER   NOT NULL REFERENCES wallet (id),
    dest_wallet      INTEGER   NOT NULL REFERENCES wallet (id),
    source_amount    DECIMAL   NOT NULL,
    dest_amount      DECIMAL   NOT NULL,
    description      TEXT,
    transfer_ref     TEXT UNIQUE,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_DATE
);





ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS transfer_detail_json TEXT;
ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS transfer_category VARCHAR(36);

CREATE TABLE IF NOT EXISTS wallet_limits
(
    id            SERIAL PRIMARY KEY,
    level         VARCHAR(10),
    owner         INTEGER REFERENCES wallet_owner (id),
    action        VARCHAR(25),
    currency      INTEGER REFERENCES new_currency (id),
    wallet_type   VARCHAR(10),
    wallet_id     INTEGER REFERENCES wallet (id),
    daily_total   DECIMAL,
    daily_count   INTEGER,
    monthly_total DECIMAL,
    monthly_count INTEGER
);

CREATE TABLE IF NOT EXISTS wallet_config
(
    name          VARCHAR(20) PRIMARY KEY,
    main_currency INTEGER NOT NULL REFERENCES new_currency (id)
);

CREATE TABLE IF NOT EXISTS withdraws
(
    id                   SERIAL PRIMARY KEY,
    uuid                 VARCHAR(36) NOT NULL,
    req_transaction_id   VARCHAR(20) NOT NULL UNIQUE,
    final_transaction_id VARCHAR(20) UNIQUE,
    currency             INTEGER NOT NULL REFERENCES new_currency (id),
    wallet               INTEGER     NOT NULL REFERENCES wallet (id),
    amount               DECIMAL     NOT NULL,
    accepted_fee         DECIMAL     NOT NULL,
    applied_fee          DECIMAL,
    dest_amount          DECIMAL,
    dest_symbol          VARCHAR(20),
    dest_network         VARCHAR(80),
    dest_address         VARCHAR(80),
    dest_notes           TEXT,
    dest_transaction_ref VARCHAR(100),
    description          TEXT,
    status_reason        TEXT,
    status               VARCHAR(20),
    create_date          TIMESTAMP   NOT NULL,
    accept_date          TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rate
(
    id               SERIAL PRIMARY KEY,
    source_symbol    INTEGER NOT NULL REFERENCES new_currency (id),
    dest_symbol      INTEGER NOT NULL REFERENCES new_currency (id),
    rate             DECIMAL,
    last_update_date TIMESTAMP,
    create_date      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS forbidden_pair
(
    id               SERIAL PRIMARY KEY,
    source_symbol    INTEGER NOT NULL REFERENCES new_currency (id),
    dest_symbol      INTEGER NOT NULL REFERENCES new_currency (id),
    last_update_date TIMESTAMP,
    create_date      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reserved_transfer
(
    id                   SERIAL PRIMARY KEY,
    reserve_number       VARCHAR(100) NOT NULL UNIQUE,
    source_symbol        INTEGER  NOT NULL REFERENCES new_currency (id),
    dest_symbol          INTEGER  NOT NULL REFERENCES new_currency (id),
    sender_wallet_type   VARCHAR(25)  NOT NULL,
    sender_uuid          VARCHAR(100) NOT NULL,
    receiver_wallet_type VARCHAR(25)  NOT NULL,
    receiver_uuid        VARCHAR(100) NOT NULL,
    source_amount        DECIMAL      NOT NULL,
    reserved_dest_amount DECIMAL      NOT NULL,
    reserve_date         TIMESTAMP,
    exp_date             TIMESTAMP,
    status               VARCHAR(25)
);

CREATE TABLE IF NOT EXISTS wallet_stat_exclusion
(
    id        SERIAL PRIMARY KEY,
    wallet_id INTEGER NOT NULL UNIQUE REFERENCES wallet (id)
);






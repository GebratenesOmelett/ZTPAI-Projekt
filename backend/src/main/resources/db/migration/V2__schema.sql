-- =====================================================================
-- Użytkownicy i uwierzytelnianie
-- =====================================================================
CREATE TABLE app_user
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_token
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- Słowniki
-- =====================================================================
CREATE TABLE city
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100)     NOT NULL,
    slug VARCHAR(100)     NOT NULL UNIQUE,
    lat  DOUBLE PRECISION NOT NULL,
    lon  DOUBLE PRECISION NOT NULL
);

CREATE TABLE district
(
    id      BIGSERIAL PRIMARY KEY,
    city_id BIGINT           NOT NULL REFERENCES city (id),
    name    VARCHAR(100)     NOT NULL,
    slug    VARCHAR(100)     NOT NULL,
    aliases TEXT             NOT NULL DEFAULT '',
    lat     DOUBLE PRECISION NOT NULL,
    lon     DOUBLE PRECISION NOT NULL,
    UNIQUE (city_id, slug)
);

CREATE TABLE feature
(
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(50)  NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

-- =====================================================================
-- Źródła danych (portale) i importy
-- =====================================================================
CREATE TABLE source
(
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    base_url       VARCHAR(500) NOT NULL,
    adapter        VARCHAR(50)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    import_cron    VARCHAR(100),
    last_import_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE import_job
(
    id                BIGSERIAL PRIMARY KEY,
    source_id         BIGINT      NOT NULL REFERENCES source (id) ON DELETE CASCADE,
    status            VARCHAR(10) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED')),
    trigger_type      VARCHAR(10) NOT NULL CHECK (trigger_type IN ('MANUAL', 'SCHEDULED', 'BOOTSTRAP')),
    requested_by      VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    fetched           INT         NOT NULL DEFAULT 0,
    created           INT         NOT NULL DEFAULT 0,
    updated           INT         NOT NULL DEFAULT 0,
    merged            INT         NOT NULL DEFAULT 0,
    deactivated       INT         NOT NULL DEFAULT 0,
    errors            INT         NOT NULL DEFAULT 0,
    expected_batches  INT,
    processed_batches INT         NOT NULL DEFAULT 0,
    error_message     TEXT
);

-- =====================================================================
-- Nieruchomości (zdeduplikowane) i oferty z portali
-- =====================================================================
CREATE TABLE property
(
    id                  BIGSERIAL PRIMARY KEY,
    fingerprint         VARCHAR(64)    NOT NULL,
    property_type       VARCHAR(20)    NOT NULL CHECK (property_type IN ('APARTMENT', 'HOUSE', 'PLOT', 'COMMERCIAL', 'ROOM')),
    transaction_type    VARCHAR(10)    NOT NULL CHECK (transaction_type IN ('SALE', 'RENT')),
    market_type         VARCHAR(10) CHECK (market_type IN ('PRIMARY', 'SECONDARY')),
    city_id             BIGINT         NOT NULL REFERENCES city (id),
    district_id         BIGINT REFERENCES district (id),
    street              VARCHAR(200),
    location            geography(Point, 4326),
    area                NUMERIC(10, 2) NOT NULL CHECK (area > 0),
    rooms               INT,
    floor               INT,
    total_floors        INT,
    year_built          INT,
    title               VARCHAR(300)   NOT NULL,
    main_photo_url      VARCHAR(1000),
    current_price       NUMERIC(14, 2),
    price_per_m2        NUMERIC(12, 2),
    price_dropped       BOOLEAN        NOT NULL DEFAULT FALSE,
    price_drop_percent  NUMERIC(6, 2)  NOT NULL DEFAULT 0,
    last_price_change_at TIMESTAMPTZ,
    last_price_drop_at  TIMESTAMPTZ,
    active_source_count INT            NOT NULL DEFAULT 0,
    first_seen_at       TIMESTAMPTZ    NOT NULL,
    last_seen_at        TIMESTAMPTZ    NOT NULL,
    status              VARCHAR(10)    NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    search_text         TEXT           NOT NULL DEFAULT '',
    search_vector       tsvector GENERATED ALWAYS AS (to_tsvector('domradar_pl', search_text)) STORED,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE property_feature
(
    property_id BIGINT NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    feature_id  BIGINT NOT NULL REFERENCES feature (id),
    PRIMARY KEY (property_id, feature_id)
);

CREATE TABLE offer
(
    id               BIGSERIAL PRIMARY KEY,
    property_id      BIGINT         NOT NULL REFERENCES property (id),
    source_id        BIGINT         NOT NULL REFERENCES source (id),
    external_id      VARCHAR(100)   NOT NULL,
    url              VARCHAR(1000),
    title            VARCHAR(300)   NOT NULL,
    description      TEXT,
    price            NUMERIC(14, 2) NOT NULL CHECK (price > 0),
    agency_name      VARCHAR(200),
    private_seller   BOOLEAN        NOT NULL DEFAULT FALSE,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    first_seen_at    TIMESTAMPTZ    NOT NULL,
    last_seen_at     TIMESTAMPTZ    NOT NULL,
    missed_imports   INT            NOT NULL DEFAULT 0,
    last_seen_job_id BIGINT,
    attributes       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_offer_source_external UNIQUE (source_id, external_id)
);

CREATE TABLE price_history
(
    id         BIGSERIAL PRIMARY KEY,
    offer_id   BIGINT         NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    price      NUMERIC(14, 2) NOT NULL,
    changed_at TIMESTAMPTZ    NOT NULL
);

CREATE TABLE photo
(
    id       BIGSERIAL PRIMARY KEY,
    offer_id BIGINT        NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    url      VARCHAR(1000) NOT NULL,
    position INT           NOT NULL DEFAULT 0
);

-- Zmaterializowane statystyki cen per dzielnica (odświeżane po imporcie)
CREATE TABLE district_price_stats
(
    district_id         BIGINT         NOT NULL REFERENCES district (id) ON DELETE CASCADE,
    property_type       VARCHAR(20)    NOT NULL,
    transaction_type    VARCHAR(10)    NOT NULL,
    median_price_per_m2 NUMERIC(12, 2) NOT NULL,
    avg_price_per_m2    NUMERIC(12, 2) NOT NULL,
    listing_count       INT            NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (district_id, property_type, transaction_type)
);

-- =====================================================================
-- Funkcje użytkownika
-- =====================================================================
CREATE TABLE saved_search
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    criteria         JSONB        NOT NULL,
    alert_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    alert_mode       VARCHAR(10)  NOT NULL DEFAULT 'DAILY' CHECK (alert_mode IN ('INSTANT', 'DAILY')),
    last_notified_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE favorite
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    property_id BIGINT      NOT NULL REFERENCES property (id) ON DELETE CASCADE,
    note        VARCHAR(2000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_favorite_user_property UNIQUE (user_id, property_id)
);

CREATE TABLE notification
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(300) NOT NULL,
    payload    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    read       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

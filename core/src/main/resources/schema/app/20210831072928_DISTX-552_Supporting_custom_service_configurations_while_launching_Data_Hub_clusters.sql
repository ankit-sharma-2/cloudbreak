-- // DISTX-552 Supporting custom service configurations while launching Data Hub clusters
-- Migration SQL that makes the change goes here.
CREATE SEQUENCE IF NOT EXISTS custom_configs_id_seq;

CREATE TABLE IF NOT EXISTS customconfigs
(
    id                bigint DEFAULT nextval('custom_configs_id_seq'::regclass)                   NOT NULL
                      CONSTRAINT customconfigs_pkey
                      PRIMARY KEY,
    name              VARCHAR(255)                                                                NOT NULL,
    crn               VARCHAR(255)                                                                NOT NULL,
    created           bigint DEFAULT (date_part('epoch'::text, now()) * (1000)::double precision) NOT NULL,
    runtimeversion    VARCHAR(255),
    account           VARCHAR(255)                                                                NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_customconfigs_crn
    ON customconfigs (crn);

CREATE UNIQUE INDEX IF NOT EXISTS idx_customconfigs_name_acoount
    ON customconfigs (name, account);

CREATE SEQUENCE IF NOT EXISTS custom_config_property_id_seq;

CREATE TABLE IF NOT EXISTS customconfigs_properties
(
    id               bigint DEFAULT nextval('custom_config_property_id_seq'::regclass) NOT NULL
                     CONSTRAINT customconfigproperty_pkey
                     PRIMARY KEY,
    name             TEXT                                                              NOT NULL,
    value            TEXT                                                              NOT NULL,
    roletype         TEXT,
    servicetype      TEXT                                                              NOT NULL,
    customconfigs_id bigint
                     CONSTRAINT fk_customconfig_property_customconfigs_id
                     REFERENCES customconfigs
                     ON UPDATE CASCADE ON DELETE CASCADE
);

ALTER TABLE cluster ADD COLUMN IF NOT EXISTS customconfigurationscrn varchar(255);

-- //@UNDO
-- SQL to undo the change goes here.
ALTER TABLE cluster DROP COLUMN IF EXISTS customconfigurationscrn;
DROP TABLE IF EXISTS customconfigs_properties;
DROP TABLE IF EXISTS customconfigs;
DROP SEQUENCE IF EXISTS custom_configs_id_seq;
DROP SEQUENCE IF EXISTS custom_config_property_id_seq;


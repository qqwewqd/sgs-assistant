CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS generals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    faction VARCHAR(20) NULL,
    is_lord TINYINT(1) NOT NULL DEFAULT 0,
    starts_hidden TINYINT(1) NOT NULL DEFAULT 0,
    initial_hp INT NULL,
    max_hp INT NULL,
    initial_armor INT NULL,
    max_armor INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_generals_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS identity_modes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    builtin TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_identity_modes_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS identity_mode_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mode_id BIGINT NOT NULL,
    player_count INT NOT NULL,
    identity_name VARCHAR(20) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    is_leader TINYINT(1) NOT NULL DEFAULT 0,
    identity_visible TINYINT(1) NOT NULL DEFAULT 0,
    allow_lord_general TINYINT(1) NOT NULL DEFAULT 0,
    initial_hp_bonus INT NOT NULL DEFAULT 0,
    max_hp_bonus INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_identity_mode_rules_name (mode_id, player_count, identity_name),
    KEY idx_identity_mode_rules_mode_count (mode_id, player_count),
    CONSTRAINT fk_identity_mode_rules_mode FOREIGN KEY (mode_id) REFERENCES identity_modes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @add_starts_hidden_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN starts_hidden TINYINT(1) NOT NULL DEFAULT 0 AFTER is_lord',
        'SELECT ''starts_hidden already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'starts_hidden'
);
PREPARE add_starts_hidden_stmt FROM @add_starts_hidden_column;
EXECUTE add_starts_hidden_stmt;
DEALLOCATE PREPARE add_starts_hidden_stmt;

SET @add_faction_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN faction VARCHAR(20) NULL AFTER image_path',
        'SELECT ''faction already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'faction'
);
PREPARE add_faction_stmt FROM @add_faction_column;
EXECUTE add_faction_stmt;
DEALLOCATE PREPARE add_faction_stmt;

SET @add_initial_hp_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN initial_hp INT NULL AFTER starts_hidden',
        'SELECT ''initial_hp already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'initial_hp'
);
PREPARE add_initial_hp_stmt FROM @add_initial_hp_column;
EXECUTE add_initial_hp_stmt;
DEALLOCATE PREPARE add_initial_hp_stmt;

SET @add_max_hp_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN max_hp INT NULL AFTER initial_hp',
        'SELECT ''max_hp already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'max_hp'
);
PREPARE add_max_hp_stmt FROM @add_max_hp_column;
EXECUTE add_max_hp_stmt;
DEALLOCATE PREPARE add_max_hp_stmt;

SET @add_initial_armor_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN initial_armor INT NULL AFTER max_hp',
        'SELECT ''initial_armor already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'initial_armor'
);
PREPARE add_initial_armor_stmt FROM @add_initial_armor_column;
EXECUTE add_initial_armor_stmt;
DEALLOCATE PREPARE add_initial_armor_stmt;

SET @add_max_armor_column := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE generals ADD COLUMN max_armor INT NULL AFTER initial_armor',
        'SELECT ''max_armor already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND column_name = 'max_armor'
);
PREPARE add_max_armor_stmt FROM @add_max_armor_column;
EXECUTE add_max_armor_stmt;
DEALLOCATE PREPARE add_max_armor_stmt;

SET @drop_generals_name_unique := (
    SELECT IF(
        COUNT(*) > 0,
        'ALTER TABLE generals DROP INDEX uk_generals_name',
        'SELECT ''uk_generals_name not found'''
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND index_name = 'uk_generals_name'
);
PREPARE drop_generals_name_unique_stmt FROM @drop_generals_name_unique;
EXECUTE drop_generals_name_unique_stmt;
DEALLOCATE PREPARE drop_generals_name_unique_stmt;

SET @create_generals_name_index := (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_generals_name ON generals (name)',
        'SELECT ''idx_generals_name already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'generals'
      AND index_name = 'idx_generals_name'
);
PREPARE create_generals_name_index_stmt FROM @create_generals_name_index;
EXECUTE create_generals_name_index_stmt;
DEALLOCATE PREPARE create_generals_name_index_stmt;

INSERT IGNORE INTO identity_modes (name, enabled, builtin)
VALUES ('标准身份局', 1, 1);

SET @standard_identity_mode_id := (
    SELECT id FROM identity_modes WHERE name = '标准身份局' ORDER BY id LIMIT 1
);

INSERT IGNORE INTO identity_mode_rules
(mode_id, player_count, identity_name, quantity, is_leader, identity_visible, allow_lord_general, initial_hp_bonus, max_hp_bonus, sort_order)
VALUES
(@standard_identity_mode_id, 2, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 2, '反贼', 1, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 3, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 3, '忠臣', 1, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 3, '反贼', 1, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 4, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 4, '忠臣', 1, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 4, '反贼', 1, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 4, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 5, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 5, '忠臣', 1, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 5, '反贼', 2, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 5, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 6, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 6, '忠臣', 1, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 6, '反贼', 3, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 6, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 7, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 7, '忠臣', 2, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 7, '反贼', 3, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 7, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 8, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 8, '忠臣', 2, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 8, '反贼', 4, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 8, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 9, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 9, '忠臣', 3, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 9, '反贼', 4, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 9, '内奸', 1, 0, 0, 0, 0, 0, 40),
(@standard_identity_mode_id, 10, '主公', 1, 1, 1, 1, 1, 1, 10),
(@standard_identity_mode_id, 10, '忠臣', 3, 0, 0, 0, 0, 0, 20),
(@standard_identity_mode_id, 10, '反贼', 4, 0, 0, 0, 0, 0, 30),
(@standard_identity_mode_id, 10, '内奸', 2, 0, 0, 0, 0, 0, 40);

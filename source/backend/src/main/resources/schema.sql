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
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_generals_name (name)
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

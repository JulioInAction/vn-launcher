-- Migración 1: esquema inicial completo.
-- Convenciones:
--   * IDs de VNDB como TEXT tal cual vienen ('v17', 'c123', 'p45').
--   * Fechas/instantes como INTEGER = segundos desde 1970 (epoch). Fáciles de comparar y restar.
--   * Booleanos como INTEGER 0/1 (SQLite no tiene tipo BOOLEAN).
--   * Rutas de imagen RELATIVAS a la carpeta de datos (p. ej. 'images/vn/v17.jpg').

-- =====================================================================
-- CACHÉ DE VNDB (se podría regenerar descargándola otra vez)
-- =====================================================================

CREATE TABLE vn (
    id                    TEXT PRIMARY KEY,
    title                 TEXT NOT NULL,
    alt_title             TEXT,              -- título original (normalmente japonés)
    description           TEXT,              -- ya limpia del formato [url]/[spoiler] de VNDB
    released              TEXT,              -- '2004-04-28', '2004', 'TBA'... por eso no es fecha
    image_url             TEXT,
    image_path            TEXT,              -- NULL hasta que se descarga la portada
    image_sexual          REAL,              -- media de votos 0..2
    image_violence        REAL,              -- media de votos 0..2
    fetched_at            INTEGER NOT NULL,
    characters_fetched_at INTEGER            -- NULL = aún no hemos pedido sus personajes (control de caché)
);

CREATE TABLE developer (
    id       TEXT PRIMARY KEY,
    name     TEXT NOT NULL,
    original TEXT
);

-- Relación N:M entre VN y desarrollador.
CREATE TABLE vn_developer (
    vn_id        TEXT NOT NULL REFERENCES vn(id) ON DELETE CASCADE,
    developer_id TEXT NOT NULL REFERENCES developer(id) ON DELETE CASCADE,
    PRIMARY KEY (vn_id, developer_id)
);

CREATE TABLE character (
    id             TEXT PRIMARY KEY,
    name           TEXT NOT NULL,
    original       TEXT,
    description    TEXT,
    image_url      TEXT,
    image_path     TEXT,
    image_sexual   REAL,
    image_violence REAL,
    fetched_at     INTEGER NOT NULL
);

-- Rol y nivel de spoiler dependen de la VN (un personaje puede ser 'primary' en una y 'side' en su secuela),
-- por eso están en la tabla intermedia y no en character.
CREATE TABLE vn_character (
    vn_id        TEXT NOT NULL REFERENCES vn(id) ON DELETE CASCADE,
    character_id TEXT NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    role         TEXT NOT NULL CHECK (role IN ('main', 'primary', 'side', 'appears')),
    spoiler      INTEGER NOT NULL DEFAULT 0 CHECK (spoiler BETWEEN 0 AND 2),
    PRIMARY KEY (vn_id, character_id)
);

-- =====================================================================
-- DATOS DEL USUARIO (no se pueden regenerar: cuidado con ellos)
-- =====================================================================

-- Una fila por VN añadida a la biblioteca, con su configuración de lanzamiento.
CREATE TABLE library_entry (
    vn_id               TEXT PRIMARY KEY REFERENCES vn(id),
    added_at            INTEGER NOT NULL,
    exe_path            TEXT,
    use_locale_emulator INTEGER NOT NULL DEFAULT 0 CHECK (use_locale_emulator IN (0, 1)),
    le_profile          TEXT,               -- GUID de perfil de Locale Emulator; NULL = perfil por defecto
    last_played_at      INTEGER
);

-- Que una fila exista aquí ES la confirmación de que ese personaje tiene ruta.
CREATE TABLE route (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    vn_id             TEXT NOT NULL REFERENCES library_entry(vn_id) ON DELETE CASCADE,
    character_id      TEXT REFERENCES character(id) ON DELETE SET NULL, -- NULL = ruta común / true end
    name              TEXT NOT NULL,
    recommended_order INTEGER,
    status            TEXT NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    notes             TEXT,
    completed_at      INTEGER
);

CREATE TABLE play_session (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    vn_id            TEXT NOT NULL REFERENCES library_entry(vn_id) ON DELETE CASCADE,
    started_at       INTEGER NOT NULL,
    ended_at         INTEGER,               -- NULL mientras la sesión está en curso
    duration_seconds INTEGER
);

-- Ajustes de la aplicación como pares clave/valor (ruta de LEProc.exe, mostrar spoilers...).
CREATE TABLE setting (
    key   TEXT PRIMARY KEY,
    value TEXT
);

-- Índices para las consultas frecuentes. Las PRIMARY KEY ya tienen índice propio;
-- estos cubren las búsquedas por la "otra" columna de las FK.
CREATE INDEX idx_vn_character_character ON vn_character(character_id);
CREATE INDEX idx_vn_developer_developer ON vn_developer(developer_id);
CREATE INDEX idx_route_vn ON route(vn_id);
CREATE INDEX idx_play_session_vn ON play_session(vn_id);

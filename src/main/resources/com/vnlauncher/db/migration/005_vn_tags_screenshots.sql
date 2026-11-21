-- Migración 5: etiquetas y capturas de pantalla de cada VN, para la ficha.
--
-- Igual que los personajes: se piden a VNDB la primera vez que se abre la ficha (y con
-- «Actualizar desde VNDB») y se guardan aquí, para que la ficha funcione sin conexión.
-- vn.details_fetched_at dice si ya se pidieron (NULL = todavía no).
ALTER TABLE vn ADD COLUMN details_fetched_at INTEGER;

-- Etiquetas de VNDB ("Drama", "Bucle temporal"...). No se guardan las marcadas como "mentira".
CREATE TABLE vn_tag (
    vn_id    TEXT NOT NULL REFERENCES vn(id) ON DELETE CASCADE,
    tag_id   TEXT NOT NULL,                   -- 'g32': no cambia aunque VNDB renombre la etiqueta
    name     TEXT NOT NULL,
    rating   REAL NOT NULL,                   -- 0..3: cuánto se aplica (media de los votos)
    spoiler  INTEGER NOT NULL DEFAULT 0 CHECK (spoiler BETWEEN 0 AND 2),
    category TEXT,                            -- 'cont' (contenido), 'ero' (sexual) o 'tech' (técnica)
    PRIMARY KEY (vn_id, tag_id)
);

-- Capturas de pantalla, en el orden de VNDB. Las imágenes aún no se descargan: solo las URLs.
CREATE TABLE vn_screenshot (
    vn_id         TEXT NOT NULL REFERENCES vn(id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,           -- 0, 1, 2... (orden de VNDB)
    url           TEXT NOT NULL,              -- imagen a tamaño completo
    thumbnail_url TEXT,                       -- miniatura
    sexual        REAL,                       -- media de votos 0..2, como las portadas
    violence      REAL,
    PRIMARY KEY (vn_id, position)
);

-- Para filtrar la biblioteca por etiqueta: "qué VNs tienen la etiqueta g32"
CREATE INDEX idx_vn_tag_tag ON vn_tag(tag_id);

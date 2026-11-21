-- Migración 7: estanterías del usuario («Mis estanterías» en la barra lateral).
--
-- Una estantería es una lista con nombre y color en la que el usuario mete las VNs que quiera
-- (Favoritas, Para la Steam Deck...). Una VN puede estar en varias. Son independientes del estado.
--
-- El color se guarda como una de 8 claves y no como un código: así se pinta con los colores del
-- tema (-vn-shelf-rosa...) y un cambio de paleta no obliga a tocar los datos.
CREATE TABLE shelf (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    color      TEXT    NOT NULL
               CHECK (color IN ('rosa', 'azul', 'naranja', 'verde', 'morado', 'turquesa', 'gris', 'coral')),
    position   INTEGER NOT NULL,           -- orden en la barra lateral (0 = la primera)
    created_at INTEGER NOT NULL
);

-- Nombres únicos sin distinguir mayúsculas (NOCASE solo pliega A-Z; ShelfService lo comprueba
-- también con acentos y eñes, y da un mensaje claro).
CREATE UNIQUE INDEX idx_shelf_name ON shelf(name COLLATE NOCASE);

-- Qué VNs hay en cada estantería. Al borrar la estantería o quitar la VN de la biblioteca, la fila
-- se va sola (ON DELETE CASCADE); la VN en sí nunca se borra por una estantería.
CREATE TABLE shelf_entry (
    shelf_id INTEGER NOT NULL REFERENCES shelf(id) ON DELETE CASCADE,
    vn_id    TEXT    NOT NULL REFERENCES library_entry(vn_id) ON DELETE CASCADE,
    added_at INTEGER NOT NULL,
    PRIMARY KEY (shelf_id, vn_id)
);

CREATE INDEX idx_shelf_entry_vn ON shelf_entry(vn_id);

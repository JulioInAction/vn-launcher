-- Migración 6: estado fijado a mano.
--
-- El estado de cada VN se calcula solo (Jugando / Pendiente / Completada, según sus rutas y si se
-- ha jugado; ver PlayStatus.of). Desde la 1.0 se puede fijar a mano desde la ficha, también a dos
-- estados que nunca salen solos: En pausa y Abandonada. NULL = automático (lo de siempre).
-- El fijado a mano se queda aunque se juegue, hasta que se vuelva a «Automático».
ALTER TABLE library_entry ADD COLUMN status_override TEXT
    CHECK (status_override IN ('PLAYING', 'PENDING', 'COMPLETED', 'PAUSED', 'DROPPED'));

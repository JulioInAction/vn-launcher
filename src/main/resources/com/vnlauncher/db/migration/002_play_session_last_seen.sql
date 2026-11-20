-- Migración 2: "latido" de las sesiones de juego.
--
-- Mientras un juego está abierto, el launcher apunta aquí cada poco la última vez que lo vio
-- funcionando. Si el launcher se cierra de golpe (cuelgue, apagón...), la sesión queda sin
-- ended_at; al volver a arrancar se cierra usando este valor, así como mucho se pierden unos
-- segundos de juego en vez de la sesión entera.
--
-- ALTER TABLE ... ADD COLUMN añade la columna a las filas existentes con valor NULL:
-- no se pierde ningún dato. (Por eso las migraciones nunca se editan: se añade otra.)
ALTER TABLE play_session ADD COLUMN last_seen_at INTEGER;

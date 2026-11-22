-- Migración 8: cuándo se vio por última vez cada VN (vista previa de la ficha).
--
-- «Ver ficha» en la búsqueda guarda la VN en la caché aunque no se añada a la biblioteca. Para que
-- esa caché no crezca para siempre, las VNs que no están en la biblioteca y no se han visto en 30
-- días se limpian al arrancar (CacheCleanupService). Esta columna dice cuándo se vieron.
ALTER TABLE vn ADD COLUMN last_viewed_at INTEGER;

-- Lo que ya había en la caché cuenta como visto cuando se pidió a VNDB: así la cuenta de los 30 días
-- empieza ahora y no se borra nada que el usuario acabe de quitar de la biblioteca.
UPDATE vn SET last_viewed_at = fetched_at;

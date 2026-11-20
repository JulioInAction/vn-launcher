-- Migración 4: portada elegida por el usuario.
--
-- Ruta RELATIVA a la carpeta de datos de la imagen que el usuario ha elegido como portada en la
-- galería ("covers/v4-cv82903.jpg": una imagen de una edición de VNDB, o "covers/v4-propia-....png":
-- un archivo suyo). NULL = la portada principal de VNDB, la de siempre.
-- Las imágenes van en covers/ y no en images/ para que "Vaciar caché" no las borre: un archivo
-- propio no se podría volver a descargar.
ALTER TABLE library_entry ADD COLUMN cover_override TEXT;

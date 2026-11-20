-- Migración 3: ejecutable secundario de cada VN.
--
-- Muchas novelas visuales traen, además del juego, un programa de configuración aparte
-- (config.exe, 設定.exe...) para la resolución, la pantalla completa o el texto. Se guarda su ruta
-- absoluta igual que exe_path; NULL = no tiene o aún no se ha elegido.
ALTER TABLE library_entry ADD COLUMN config_exe_path TEXT;

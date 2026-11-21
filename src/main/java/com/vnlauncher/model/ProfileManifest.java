package com.vnlauncher.model;

/**
 * manifest.json de un perfil exportado: qué es, de qué versión y cuánto trae. Lo lee el diálogo
 * «Importar perfil» para enseñar el resumen sin tener que abrir la base de datos.
 *
 * <p>Jackson lo convierte a JSON y de vuelta (por eso module-info abre este paquete). Si a una
 * versión futura le hace falta otro campo, se añade: las versiones viejas ignoran los que no
 * conocen, y a las nuevas los que falten les llegan a 0 o null.
 *
 * @param format          versión del formato del perfil (sube solo si cambia de forma incompatible)
 * @param app             siempre «VN Launcher»: para quien abra el zip a mano
 * @param appVersion      versión de la app que lo exportó
 * @param schemaVersion   versión del esquema de su base de datos
 * @param exportedAt      cuándo se exportó, «2026-11-21T10:42:03» (texto: Jackson no trae fechas de serie)
 * @param novels          novelas en la biblioteca
 * @param routes          rutas apuntadas
 * @param playtimeSeconds tiempo de juego total
 * @param covers          portadas propias incluidas
 * @param shelves         estanterías (0 en perfiles de antes de que existieran: el campo no está)
 */
public record ProfileManifest(int format, String app, String appVersion, int schemaVersion, String exportedAt,
                              int novels, int routes, long playtimeSeconds, int covers, int shelves) {
}

package com.vnlauncher.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo JSON de una consulta a VNDB, por ejemplo:
 * <pre>
 * {"filters": ["search", "=", "clannad"], "fields": "id, title", "sort": "searchrank", "results": 25}
 * </pre>
 *
 * @param filters filtros en el formato de VNDB. Es Object porque pueden ser listas anidadas,
 *                p. ej. ["vn", "=", ["id", "=", "v4"]] para pedir personajes de una VN (Fase 3).
 * @param fields  campos que queremos que nos devuelva, separados por comas.
 * @param sort    campo de ordenación; null = el de por defecto.
 * @param results número de resultados por página (máximo 100).
 * @param page    página, empezando en 1; null = la primera.
 */
// NON_NULL: los campos que valen null no se escriben en el JSON (VNDB usará su valor por defecto).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VndbQuery(Object filters, String fields, String sort, Integer results, Integer page) {
}

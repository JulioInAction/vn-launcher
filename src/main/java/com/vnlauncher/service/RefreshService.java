package com.vnlauncher.service;

import com.vnlauncher.api.VndbException;
import com.vnlauncher.model.VisualNovel;

import java.sql.SQLException;
import java.util.Optional;

/**
 * "Actualizar desde VNDB" en la ficha: vuelve a pedir los datos de una VN de la biblioteca
 * (título, descripción, portada, desarrolladores), sus personajes principales y sus etiquetas y
 * capturas.
 *
 * <p>Normalmente la aplicación NO vuelve a preguntar a VNDB por algo que ya tiene (es la caché que
 * nos permite respetar el límite de 200 peticiones). Esto es la excepción, y la pide el usuario.
 * Coste: 3 peticiones (1 de la VN + 1 de personajes, o más si tuviera más de 100, + 1 de
 * etiquetas y capturas).
 *
 * <p>Lo que es del usuario (rutas, tiempo de juego, ejecutable) no se toca.
 */
public class RefreshService {

    /**
     * @param found      false si VNDB ya no tiene la VN (se conservan los datos que había)
     * @param characters cuántos personajes principales tiene ahora
     */
    public record Result(boolean found, int characters) {
    }

    private final CatalogService catalog;
    private final LibraryService library;
    private final CharacterService characters;
    private final VnDetailsService details;

    public RefreshService(CatalogService catalog, LibraryService library, CharacterService characters,
                          VnDetailsService details) {
        this.catalog = catalog;
        this.library = library;
        this.characters = characters;
        this.details = details;
    }

    /** Llamar en segundo plano: hace peticiones a VNDB y descarga imágenes. */
    public Result refresh(String vnId) throws VndbException, SQLException, InterruptedException {
        Optional<VisualNovel> fresh = catalog.fetchById(vnId);
        if (fresh.isEmpty()) {
            return new Result(false, 0);
        }
        library.updateFromVndb(fresh.get());
        int count = characters.refresh(vnId).size();
        details.refresh(vnId);
        return new Result(true, count);
    }
}

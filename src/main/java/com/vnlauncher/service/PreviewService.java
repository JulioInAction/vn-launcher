package com.vnlauncher.service;

import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.db.repo.VnRepository;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnDetails;

import java.sql.SQLException;
import java.time.Clock;

/**
 * Lo que se guarda al abrir la vista previa de la ficha («Ver ficha» en la búsqueda) o al añadir una
 * VN desde ella.
 *
 * <p>Mientras se navega por los resultados, las etiquetas y capturas viven solo en memoria
 * ({@link CatalogService#cachedDetails}); no se escribe nada en la base de datos. Solo al «Ver ficha»
 * o al añadir se guardan, y así la ficha no tiene que volver a pedirlas a VNDB. Las VNs que se ven y
 * no se añaden las borra {@link CacheCleanupService} a los 30 días.
 */
public class PreviewService {

    private final VnRepository vns;
    private final VnDetailsRepository details;
    private final Clock clock;

    public PreviewService(VnRepository vns, VnDetailsRepository details, Clock clock) {
        this.vns = vns;
        this.details = details;
        this.clock = clock;
    }

    /**
     * Guarda la VN en la caché (cuenta como vista ahora) y, si se tienen, sus etiquetas y capturas.
     * Lo que ya estuviera guardado de ellas no se pisa: pudo traerlo «Actualizar desde VNDB».
     *
     * @param searchDetails lo que la búsqueda ya sabía de la VN; null si aún no ha llegado
     */
    public void remember(VisualNovel vn, VnDetails searchDetails) throws SQLException {
        vns.save(vn, clock.instant());
        if (searchDetails != null && !details.isFetched(vn.id())) {
            details.saveForVn(vn.id(), searchDetails, clock.instant());
        }
    }
}

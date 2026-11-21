package com.vnlauncher.service;

import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.VndbException;
import com.vnlauncher.api.dto.TagDto;
import com.vnlauncher.api.dto.VnDetailsDto;
import com.vnlauncher.db.repo.VnDetailsRepository;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnScreenshot;
import com.vnlauncher.model.VnTag;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Etiquetas y capturas de pantalla de la ficha.
 *
 * <p>Misma caché que los personajes ({@link CharacterService}): la primera vez que se abre la
 * ficha se piden a VNDB (1 petición, que pasa por el RateLimiter) y se guardan en vn_tag y
 * vn_screenshot; desde entonces salen de la base de datos, también sin conexión. «Actualizar
 * desde VNDB» las vuelve a pedir.
 */
public class VnDetailsService {

    private final VndbClient vndb;
    private final VnDetailsRepository repository;
    private final Clock clock;

    public VnDetailsService(VndbClient vndb, VnDetailsRepository repository, Clock clock) {
        this.vndb = vndb;
        this.repository = repository;
        this.clock = clock;
    }

    /** Etiquetas y capturas de la VN, pidiéndolas a VNDB si aún no se tienen. Llamar en segundo plano. */
    public VnDetails get(String vnId) throws VndbException, SQLException {
        if (!repository.isFetched(vnId)) {
            fetchAndSave(vnId);
        }
        return repository.findByVn(vnId);
    }

    /** «Actualizar desde VNDB»: las vuelve a pedir aunque ya estuvieran guardadas. */
    public VnDetails refresh(String vnId) throws VndbException, SQLException {
        fetchAndSave(vnId);
        return repository.findByVn(vnId);
    }

    /** Ids de las VNs que tienen la etiqueta (solo las que ya tienen sus etiquetas guardadas). */
    public Set<String> vnIdsWithTag(String tagId) throws SQLException {
        return repository.vnIdsWithTag(tagId);
    }

    /** VNs de la biblioteca cuyas etiquetas aún no se conocen (nunca se abrió su ficha). */
    public int countLibraryWithoutTags() throws SQLException {
        return repository.countLibraryWithoutDetails();
    }

    private void fetchAndSave(String vnId) throws VndbException, SQLException {
        // Si VNDB ya no tiene la VN, se guarda vacío: así no se vuelve a preguntar cada vez
        VnDetails details = vndb.vnTagsAndScreenshots(vnId)
                .map(VnDetailsService::toModel)
                .orElse(VnDetails.EMPTY);
        repository.saveForVn(vnId, details, clock.instant());
    }

    /**
     * DTO → modelo. Fuera las etiquetas "mentira" (VNDB las marca así cuando NO se aplican) y las
     * que llegan incompletas. Las spoiler y las sexuales se guardan: la ficha decide si las enseña
     * según los ajustes, que pueden cambiar después.
     */
    static VnDetails toModel(VnDetailsDto dto) {
        List<VnTag> tags = dto.tags() == null ? List.of() : dto.tags().stream()
                .filter(t -> !Boolean.TRUE.equals(t.lie()))
                .filter(t -> t.id() != null && t.name() != null && t.rating() != null)
                .sorted(Comparator.comparingDouble(TagDto::rating).reversed())
                .map(t -> new VnTag(t.id(), t.name(), t.rating(),
                        t.spoiler() == null ? 0 : t.spoiler(), t.category()))
                .toList();
        List<VnScreenshot> screenshots = dto.screenshots() == null ? List.of() : dto.screenshots().stream()
                .filter(s -> s.url() != null)
                .map(s -> new VnScreenshot(s.url(), s.thumbnail(), s.sexual(), s.violence()))
                .toList();
        return new VnDetails(tags, screenshots);
    }
}

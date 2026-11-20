package com.vnlauncher.service;

import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.VndbException;
import com.vnlauncher.api.dto.CharacterDto;
import com.vnlauncher.api.dto.CharacterVnDto;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.db.repo.CharacterRepository;
import com.vnlauncher.model.CharacterRole;
import com.vnlauncher.model.GameCharacter;
import com.vnlauncher.model.VnCharacter;
import com.vnlauncher.util.VndbMarkup;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Personajes principales de una VN (candidatas a heroína).
 *
 * <p><b>Caché:</b> la primera vez que se piden los personajes de una VN se consultan a VNDB
 * y se guardan; desde entonces se leen SOLO de la base de datos (vn.characters_fetched_at
 * dice si ya se pidieron). Las imágenes que falten se descargan cada vez que se piden,
 * igual que las portadas, pero eso no gasta peticiones de la API.
 */
public class CharacterService {

    private static final System.Logger LOG = System.getLogger(CharacterService.class.getName());

    private final VndbClient vndb;
    private final CharacterRepository repository;
    private final ImageCacheService imageCache;
    private final Clock clock;

    public CharacterService(VndbClient vndb, CharacterRepository repository,
                            ImageCacheService imageCache, Clock clock) {
        this.vndb = vndb;
        this.repository = repository;
        this.imageCache = imageCache;
        this.clock = clock;
    }

    /**
     * Personajes principales de la VN (ya guardada en la BD), descargándolos si hace falta.
     * Lento la primera vez (red); después, inmediato. Llamar siempre en segundo plano.
     */
    public List<VnCharacter> getPrimaryCharacters(String vnId)
            throws VndbException, SQLException, InterruptedException {
        if (!repository.isFetched(vnId)) {
            List<VnCharacter> fetched = vndb.primaryCharactersOfVn(vnId).stream()
                    .map(dto -> toModel(dto, vnId))
                    .flatMap(Optional::stream) // quita los Optional vacíos
                    .filter(vc -> vc.role() == CharacterRole.PRIMARY)
                    .toList();
            repository.saveForVn(vnId, fetched, clock.instant());
        }
        List<VnCharacter> characters = repository.findByVn(vnId);
        if (downloadMissingImages(characters) > 0) {
            characters = repository.findByVn(vnId); // releer para tener las rutas nuevas
        }
        return characters;
    }

    /**
     * DTO → modelo, para la VN indicada. Vacío si el personaje no tiene entrada para esa VN.
     *
     * <p>Si la VN aparece varias veces en dto.vns() (una por versión), nos quedamos con:
     * <ul>
     *   <li>el rol MÁS importante (si en alguna versión es principal, lo tratamos como principal);</li>
     *   <li>el spoiler MÁS BAJO (si en alguna versión no es spoiler, su existencia ya es pública).</li>
     * </ul>
     */
    static Optional<VnCharacter> toModel(CharacterDto dto, String vnId) {
        CharacterRole role = null;
        int spoiler = Integer.MAX_VALUE;
        for (CharacterVnDto appearance : dto.vns() == null ? List.<CharacterVnDto>of() : dto.vns()) {
            if (!vnId.equals(appearance.id())) {
                continue;
            }
            CharacterRole r = CharacterRole.fromApi(appearance.role());
            if (role == null || r.moreImportantThan(role)) {
                role = r;
            }
            spoiler = Math.min(spoiler, appearance.spoiler());
        }
        if (role == null) {
            return Optional.empty();
        }
        ImageDto image = dto.image();
        GameCharacter character = new GameCharacter(
                dto.id(),
                dto.name(),
                dto.original(),
                VndbMarkup.toPlainText(dto.description()),
                image == null ? null : image.url(),
                null,
                image == null ? null : image.sexual(),
                image == null ? null : image.violence());
        return Optional.of(new VnCharacter(character, role, spoiler));
    }

    /** Descarga las imágenes de personaje que falten. Devuelve cuántas ha descargado. */
    private int downloadMissingImages(List<VnCharacter> characters) throws SQLException, InterruptedException {
        int downloaded = 0;
        for (VnCharacter vc : characters) {
            GameCharacter c = vc.character();
            if (c.imageUrl() == null || imageCache.isCached(c.imagePath())) {
                continue;
            }
            try {
                repository.updateImagePath(c.id(), imageCache.download(c.imageUrl(), "char", c.id()));
                downloaded++;
            } catch (IOException e) {
                // Sin conexión, etc.: se muestra "Sin imagen" y se reintenta la próxima vez.
                LOG.log(System.Logger.Level.WARNING, "No se pudo descargar la imagen de " + c.id(), e);
            }
        }
        return downloaded;
    }
}

package com.vnlauncher.service;

import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.VndbException;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.api.dto.ProducerDto;
import com.vnlauncher.api.dto.RelationDto;
import com.vnlauncher.api.dto.TagDto;
import com.vnlauncher.api.dto.VnDetailsDto;
import com.vnlauncher.api.dto.VnDto;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.SearchHit;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.model.VnDetails;
import com.vnlauncher.model.VnExtras;
import com.vnlauncher.model.VnStats;
import com.vnlauncher.util.VndbMarkup;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lógica de negocio del catálogo: buscar VNs en VNDB, los datos del panel de la búsqueda y los
 * datos actuales de una VN. Es el único sitio que convierte DTOs de la API en objetos del modelo.
 */
public class CatalogService {

    static final int MAX_SEARCH_RESULTS = 25;
    /** Capturas que se muestran en el panel de la búsqueda. */
    static final int SCREENSHOTS = 3;

    /**
     * Prioridad de las relaciones para elegir "la más importante" (primero la que está antes) y
     * cómo se dice. La relación indica qué ES la otra VN para esta: si la otra es la precuela
     * ("preq"), esta es su secuela.
     */
    private static final List<Map.Entry<String, String>> RELATIONS = List.of(
            Map.entry("orig", "Fandisc de "),
            Map.entry("preq", "Secuela de "),
            Map.entry("par", "Historia secundaria de "),
            Map.entry("alt", "Versión alternativa de "),
            Map.entry("seq", "Precuela de "),
            Map.entry("fan", "Tiene un fandisc: "),
            Map.entry("side", "Tiene una historia secundaria: "),
            Map.entry("ser", "De la misma serie que "),
            Map.entry("set", "Mismo mundo que "),
            Map.entry("char", "Comparte personajes con "));

    private final VndbClient vndb;
    /**
     * Caché en memoria de los datos del panel: volver a seleccionar un resultado no repite la
     * petición. ConcurrentHashMap porque se llena desde hilos en segundo plano. Dura lo que dure
     * la app abierta (no se guarda en disco: son datos que cambian y solo sirven para curiosear).
     */
    private final Map<String, VnExtras> extrasCache = new ConcurrentHashMap<>();
    /**
     * Lo mismo que extrasCache pero entero (todas las etiquetas, también spoiler, y todas las capturas),
     * tal como lo guarda la ficha. Así «Ver ficha» y «Añadir» lo guardan sin otra petición.
     */
    private final Map<String, VnDetails> detailsCache = new ConcurrentHashMap<>();

    public CatalogService(VndbClient vndb) {
        this.vndb = vndb;
    }

    /**
     * Busca en VNDB. UNA petición, que trae también los datos ligeros del panel (duración,
     * valoración, idiomas...). Un texto vacío no gasta petición: devuelve una lista vacía.
     */
    public List<SearchHit> search(String text) throws VndbException {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return vndb.searchVn(text.strip(), MAX_SEARCH_RESULTS).stream()
                .map(dto -> new SearchHit(toModel(dto), toStats(dto)))
                .toList();
    }

    /**
     * Etiquetas, capturas y relación principal de una VN, para el panel de la búsqueda. La primera
     * vez gasta 1 petición (pasa por el RateLimiter, como todas); después sale de la caché.
     */
    public VnExtras extras(String vnId) throws VndbException {
        VnExtras cached = extrasCache.get(vnId);
        if (cached != null) {
            return cached;
        }
        Optional<VnDetailsDto> dto = vndb.vnDetails(vnId);
        dto.ifPresent(found -> detailsCache.put(vnId, VnDetailsService.toModel(found)));
        VnExtras extras = dto.map(CatalogService::toExtras).orElse(new VnExtras(List.of(), List.of(), null));
        extrasCache.put(vnId, extras);
        return extras;
    }

    /** Si los datos del panel ya están en la caché (para no mostrar "Cargando..." un instante). */
    public Optional<VnExtras> cachedExtras(String vnId) {
        return Optional.ofNullable(extrasCache.get(vnId));
    }

    /**
     * Etiquetas y capturas completas de un resultado, si ya se pidieron (están en memoria). Vacío si
     * aún no, o si VNDB no tiene la VN.
     */
    public Optional<VnDetails> cachedDetails(String vnId) {
        return Optional.ofNullable(detailsCache.get(vnId));
    }

    static VnStats toStats(VnDto dto) {
        return new VnStats(dto.olang(), dto.lengthMinutes(), dto.lengthVotes(), dto.rating(), dto.votecount(),
                dto.languages(), dto.platforms(), dto.devstatus());
    }

    /** Etiquetas sin spoilers ni "mentiras", de mayor a menor puntuación; las primeras capturas. */
    static VnExtras toExtras(VnDetailsDto dto) {
        List<VnExtras.Tag> tags = dto.tags() == null ? List.of() : dto.tags().stream()
                .filter(t -> t.spoiler() == null || t.spoiler() == 0)
                .filter(t -> !Boolean.TRUE.equals(t.lie()))
                .filter(t -> t.name() != null && t.rating() != null)
                // sorted es estable: a igual puntuación se mantiene el orden de VNDB
                .sorted(Comparator.comparingDouble(TagDto::rating).reversed())
                .map(t -> new VnExtras.Tag(t.name(), t.rating(), t.category()))
                .toList();
        List<VnExtras.Screenshot> screenshots = dto.screenshots() == null ? List.of() : dto.screenshots().stream()
                .limit(SCREENSHOTS)
                .map(s -> new VnExtras.Screenshot(s.thumbnail() != null ? s.thumbnail() : s.url(), s.url(),
                        s.sexual(), s.violence()))
                .toList();
        return new VnExtras(tags, screenshots, mainRelation(dto.relations()));
    }

    /**
     * La relación más importante, ya en texto: "Fandisc de Fate/stay night", "Secuela de...".
     * Primero las oficiales y, entre ellas, por tipo (ver RELATIONS). Las no oficiales solo si no
     * hay ninguna oficial: en VNDB, Fate/stay night tiene una "precuela" no oficial (una obra de
     * fans) que si no la convertiría en "Secuela de Fate/Otome". null si no hay relaciones.
     */
    static String mainRelation(List<RelationDto> relations) {
        if (relations == null) {
            return null;
        }
        for (boolean official : new boolean[] {true, false}) {
            for (Map.Entry<String, String> type : RELATIONS) {
                Optional<RelationDto> match = relations.stream()
                        .filter(r -> type.getKey().equals(r.relation()))
                        .filter(r -> Boolean.TRUE.equals(r.relationOfficial()) == official)
                        .findFirst();
                if (match.isPresent()) {
                    return type.getValue() + match.get().title();
                }
            }
        }
        return null;
    }

    /**
     * Datos actuales de una VN en VNDB (para "Actualizar desde VNDB"). Gasta 1 petición.
     * Vacío si VNDB ya no tiene esa VN.
     */
    public Optional<VisualNovel> fetchById(String vnId) throws VndbException {
        return vndb.vnById(vnId).map(CatalogService::toModel);
    }

    /** DTO → modelo. Package-private (sin "public") para poder testearlo desde el mismo paquete. */
    static VisualNovel toModel(VnDto dto) {
        ImageDto image = dto.image();
        List<ProducerDto> producers = dto.developers() == null ? List.of() : dto.developers();
        return new VisualNovel(
                dto.id(),
                dto.title(),
                dto.alttitle(),
                VndbMarkup.toPlainText(dto.description()),
                dto.released(),
                image == null ? null : image.url(),
                null, // imagePath: un resultado de búsqueda aún no tiene portada descargada
                image == null ? null : image.sexual(),
                image == null ? null : image.violence(),
                producers.stream().map(p -> new Developer(p.id(), p.name(), p.original())).toList()
        );
    }
}

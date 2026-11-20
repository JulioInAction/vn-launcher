package com.vnlauncher.service;

import com.vnlauncher.api.VndbClient;
import com.vnlauncher.api.VndbException;
import com.vnlauncher.api.dto.ImageDto;
import com.vnlauncher.api.dto.ProducerDto;
import com.vnlauncher.api.dto.VnDto;
import com.vnlauncher.model.Developer;
import com.vnlauncher.model.VisualNovel;
import com.vnlauncher.util.VndbMarkup;

import java.util.List;
import java.util.Optional;

/**
 * Lógica de negocio del catálogo: buscar VNs en VNDB y pedir los datos actuales de una.
 * Es el único sitio que convierte DTOs de la API en objetos del modelo.
 */
public class CatalogService {

    static final int MAX_SEARCH_RESULTS = 25;

    private final VndbClient vndb;

    public CatalogService(VndbClient vndb) {
        this.vndb = vndb;
    }

    /** Busca en VNDB. Un texto vacío no gasta petición: devuelve una lista vacía. */
    public List<VisualNovel> search(String text) throws VndbException {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return vndb.searchVn(text.strip(), MAX_SEARCH_RESULTS).stream()
                .map(CatalogService::toModel)
                .toList();
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

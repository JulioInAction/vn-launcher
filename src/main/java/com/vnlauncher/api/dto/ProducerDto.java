package com.vnlauncher.api.dto;

/** Productor/desarrollador de VNDB (id con prefijo "p", p. ej. "p24" = Key). */
public record ProducerDto(String id, String name, String original) {
}

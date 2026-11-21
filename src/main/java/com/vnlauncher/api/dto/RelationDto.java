package com.vnlauncher.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Otra VN relacionada con esta.
 *
 * @param relation qué ES la otra VN para esta: "preq" (precuela), "seq" (secuela), "orig" (el
 *                 juego original, si esta es un fandisc), "fan", "par", "side", "alt", "set",
 *                 "ser" o "char"
 */
public record RelationDto(String id, String title, String relation,
                          @JsonProperty("relation_official") Boolean relationOfficial) {
}

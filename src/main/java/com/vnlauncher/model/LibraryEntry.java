package com.vnlauncher.model;

import java.time.Instant;

/**
 * Una VN que el usuario ha añadido a su biblioteca.
 * En la Fase 5 se ampliará con la configuración de lanzamiento (exe, Locale Emulator...).
 */
public record LibraryEntry(VisualNovel vn, Instant addedAt) {
}

package com.vnlauncher.ui;

import com.vnlauncher.app.AppContext;
import com.vnlauncher.model.LibraryEntry;
import com.vnlauncher.model.VisualNovel;

import java.nio.file.Path;

/**
 * La portada que se ve de una VN de la biblioteca (ficha, cuadrícula, «Continuar», «Recientes»):
 * la elegida en la galería o, si no hay, la de VNDB.
 */
final class Covers {

    private Covers() {
    }

    /** Archivo de la portada que hay que mostrar, o null si aún no hay ninguna en disco. */
    static Path file(AppContext ctx, LibraryEntry entry) {
        return ctx.coverService().effectiveCover(entry);
    }

    /**
     * Si hay que difuminarla. La valoración de VNDB es la de SU portada; una elegida por el usuario
     * (que la ha visto en la galería antes de elegirla) se muestra tal cual.
     */
    static boolean sensitive(AppContext ctx, LibraryEntry entry) {
        if (ctx.coverService().hasOverride(entry)) {
            return false;
        }
        VisualNovel vn = entry.vn();
        return ctx.contentFilter().isSensitive(vn.imageSexual(), vn.imageViolence());
    }
}

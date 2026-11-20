package com.vnlauncher.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga las fuentes incluidas en src/main/resources/fonts/ (licencia OFL, ver fonts/README.md).
 *
 * <p>Hay que llamar a {@link #load()} ANTES de crear la primera escena: así, cuando JavaFX aplica
 * el CSS y encuentra {@code -fx-font-family: "Inter"}, la fuente ya está registrada.
 *
 * <p>Ojo con los pesos: JavaFX registra cada archivo con el nombre de familia que trae dentro.
 * Inter Medium e Inter SemiBold se llaman así ("Inter Medium", "Inter SemiBold"), no "Inter" con
 * otro peso; por eso en el CSS se usan como familias distintas. Zen Kaku Gothic New Bold se registra
 * como familia "Zen Kaku Gothic New" con estilo Bold, y la Medium como "Zen Kaku Gothic New Medium".
 */
public final class Fonts {

    private static final System.Logger LOG = System.getLogger(Fonts.class.getName());

    private static final String[] FILES = {
            "Inter-Regular.ttf",
            "Inter-Medium.ttf",
            "Inter-SemiBold.ttf",
            "ZenKakuGothicNew-Bold.ttf",
            "ZenKakuGothicNew-Medium.ttf", // títulos y nombres originales en japonés (500)
    };

    private Fonts() {
    }

    /** @return las familias cargadas (para comprobarlo en los registros o en tests) */
    public static List<String> load() {
        List<String> loaded = new ArrayList<>();
        for (String file : FILES) {
            // Ruta absoluta (empieza por "/"): la carpeta fonts/ está en la raíz de los recursos
            try (InputStream in = Fonts.class.getResourceAsStream("/fonts/" + file)) {
                Font font = in == null ? null : Font.loadFont(in, 14);
                if (font == null) {
                    LOG.log(System.Logger.Level.WARNING, "No se pudo cargar la fuente {0}", file);
                } else {
                    loaded.add(font.getFamily() + " / " + font.getName());
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Error leyendo la fuente " + file, e);
            }
        }
        LOG.log(System.Logger.Level.DEBUG, "Fuentes cargadas: {0}", loaded);
        return loaded;
    }
}

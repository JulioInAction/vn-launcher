package com.vnlauncher.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textos de los datos del panel de la búsqueda: "unas 51 h", "8,1 · 5.944 votos", "Japonés,
 * inglés...", "Windows, PS Vita...", "Terminada".
 *
 * <p>Los nombres de idiomas y plataformas van en tablas propias en vez de pedírselos a Java
 * ({@code Locale.getDisplayLanguage}): para darlos en español, Java necesita el módulo
 * jdk.localedata, que el instalador no incluye para ocupar menos.
 */
public final class VnFacts {

    /** Cuántos idiomas o plataformas se nombran antes de "y N más". */
    static final int MAX_LISTED = 4;

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("ja", "japonés"), Map.entry("en", "inglés"), Map.entry("es", "español"),
            Map.entry("zh", "chino"), Map.entry("zh-Hans", "chino simplificado"),
            Map.entry("zh-Hant", "chino tradicional"), Map.entry("ko", "coreano"), Map.entry("fr", "francés"),
            Map.entry("de", "alemán"), Map.entry("it", "italiano"), Map.entry("ru", "ruso"),
            Map.entry("pt-br", "portugués (Brasil)"), Map.entry("pt-pt", "portugués"), Map.entry("pl", "polaco"),
            Map.entry("uk", "ucraniano"), Map.entry("vi", "vietnamita"), Map.entry("id", "indonesio"),
            Map.entry("th", "tailandés"), Map.entry("tr", "turco"), Map.entry("ar", "árabe"),
            Map.entry("he", "hebreo"), Map.entry("nl", "neerlandés"), Map.entry("sv", "sueco"),
            Map.entry("cs", "checo"), Map.entry("hu", "húngaro"), Map.entry("ro", "rumano"),
            Map.entry("ca", "catalán"), Map.entry("eu", "euskera"), Map.entry("fi", "finés"),
            Map.entry("da", "danés"), Map.entry("no", "noruego"), Map.entry("el", "griego"),
            Map.entry("bg", "búlgaro"), Map.entry("hr", "croata"), Map.entry("sk", "eslovaco"),
            Map.entry("ms", "malayo"), Map.entry("fa", "persa"), Map.entry("hi", "hindi"),
            Map.entry("ta", "tamil"), Map.entry("la", "latín"), Map.entry("eo", "esperanto"),
            Map.entry("lt", "lituano"), Map.entry("lv", "letón"), Map.entry("sr", "serbio"),
            Map.entry("sl", "esloveno"), Map.entry("mk", "macedonio"), Map.entry("ga", "irlandés"));

    private static final Map<String, String> PLATFORMS = Map.ofEntries(
            Map.entry("win", "Windows"), Map.entry("lin", "Linux"), Map.entry("mac", "macOS"),
            Map.entry("web", "Navegador"), Map.entry("ios", "iOS"), Map.entry("and", "Android"),
            Map.entry("mob", "Móvil"), Map.entry("dos", "DOS"), Map.entry("dvd", "DVD"),
            Map.entry("bdp", "Blu-ray"), Map.entry("swi", "Switch"), Map.entry("sw2", "Switch 2"),
            Map.entry("ps1", "PlayStation"), Map.entry("ps2", "PS2"), Map.entry("ps3", "PS3"),
            Map.entry("ps4", "PS4"), Map.entry("ps5", "PS5"), Map.entry("psp", "PSP"),
            Map.entry("psv", "PS Vita"), Map.entry("xb1", "Xbox"), Map.entry("xb3", "Xbox 360"),
            Map.entry("xbo", "Xbox One"), Map.entry("xxs", "Xbox Series"), Map.entry("nds", "Nintendo DS"),
            Map.entry("n3d", "Nintendo 3DS"), Map.entry("wii", "Wii"), Map.entry("wiu", "Wii U"),
            Map.entry("gba", "Game Boy Advance"), Map.entry("gbc", "Game Boy Color"), Map.entry("drc", "Dreamcast"),
            Map.entry("sat", "Saturn"), Map.entry("smd", "Mega Drive"), Map.entry("scd", "Mega-CD"),
            Map.entry("pce", "PC Engine"), Map.entry("pcf", "PC-FX"), Map.entry("p88", "PC-88"),
            Map.entry("p98", "PC-98"), Map.entry("x68", "X68000"), Map.entry("fmt", "FM Towns"),
            Map.entry("msx", "MSX"), Map.entry("vnd", "VNDS"), Map.entry("tdo", "3DO"),
            Map.entry("oth", "otras"));

    private VnFacts() {
    }

    /**
     * Duración media: "unas 51 h", "unos 45 min" o "Sin datos" si no hay votos suficientes.
     * Menos de hora y media se dice en minutos (redondeados a 5); más, en horas.
     */
    public static String length(Integer minutes, Integer votes) {
        if (minutes == null || minutes <= 0 || (votes != null && votes == 0)) {
            return "Sin datos";
        }
        if (minutes < 90) {
            return "unos " + Math.max(5, Math.round(minutes / 5.0) * 5) + " min";
        }
        return "unas " + Math.round(minutes / 60.0) + " h";
    }

    /**
     * Valoración en escala de 10 con los votos: "8,1 · 5.944 votos". VNDB la da de 10 a 100.
     * "Sin votos" si no hay.
     */
    public static String rating(Double rating, Integer votes) {
        if (rating == null || votes == null || votes == 0) {
            return "Sin votos";
        }
        String score = String.format(Locale.ROOT, "%.1f", rating / 10.0).replace('.', ',');
        return score + " · " + thousands(votes) + (votes == 1 ? " voto" : " votos");
    }

    /** 1500 → "1.500" (separador de miles español; sin depender de los datos de idioma de Java). */
    static String thousands(int n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', '.');
    }

    /**
     * "Japonés, inglés, español, italiano y 4 más". El idioma original va el primero; los demás en
     * el orden de VNDB. Códigos desconocidos se muestran tal cual.
     */
    public static String languages(List<String> codes, String original) {
        List<String> ordered = new ArrayList<>(codes);
        if (original != null && ordered.remove(original)) {
            ordered.addFirst(original);
        }
        return list(ordered.stream().map(c -> LANGUAGES.getOrDefault(c, c)).toList());
    }

    /** "Windows, Android, PSP, PS Vita y 2 más". */
    public static String platforms(List<String> codes) {
        return list(codes.stream().map(c -> PLATFORMS.getOrDefault(c, c.toUpperCase(Locale.ROOT))).toList());
    }

    /** Estado de desarrollo: "Terminada", "En desarrollo", "Cancelada"; null si no se sabe. */
    public static String devStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "Terminada";
            case 1 -> "En desarrollo";
            case 2 -> "Cancelada";
            default -> null;
        };
    }

    /** Une los nombres con comas, la primera letra en mayúscula, y corta en "y N más". */
    static String list(List<String> names) {
        if (names.isEmpty()) {
            return "Sin datos";
        }
        List<String> shown = names.subList(0, Math.min(MAX_LISTED, names.size()));
        String text = String.join(", ", shown);
        int rest = names.size() - shown.size();
        if (rest > 0) {
            text += " y " + rest + " más";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}

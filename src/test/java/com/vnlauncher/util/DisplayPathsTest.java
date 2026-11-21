package com.vnlauncher.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DisplayPathsTest {

    private static final Map<String, String> WINDOWS = Map.of(
            "LOCALAPPDATA", "C:\\Users\\Julio\\AppData\\Local",
            "APPDATA", "C:\\Users\\Julio\\AppData\\Roaming",
            "USERPROFILE", "C:\\Users\\Julio");

    @Test
    void windowsPathsUseTheMostSpecificVariable() {
        assertEquals("%LOCALAPPDATA%\\VNLauncher\\logs\\vn-launcher0.log", DisplayPaths.forDisplay(
                "C:\\Users\\Julio\\AppData\\Local\\VNLauncher\\logs\\vn-launcher0.log", WINDOWS, "C:\\Users\\Julio"));
        assertEquals("%USERPROFILE%\\Juegos\\CLANNAD", DisplayPaths.forDisplay(
                "C:\\Users\\Julio\\Juegos\\CLANNAD", WINDOWS, "C:\\Users\\Julio"));
    }

    @Test
    void windowsIsCaseInsensitiveButDoesNotMixSimilarNames() {
        assertEquals("%USERPROFILE%\\x", DisplayPaths.forDisplay("c:\\users\\julio\\x", WINDOWS, null));
        assertEquals("C:\\Users\\Julia\\x", DisplayPaths.forDisplay("C:\\Users\\Julia\\x", WINDOWS, null),
                "Julia no está dentro de Julio");
        assertEquals("C:\\Users\\Julio2\\x", DisplayPaths.forDisplay("C:\\Users\\Julio2\\x", WINDOWS, null));
    }

    @Test
    void elsewhereTheHomeFolderIsATilde() {
        assertEquals("~/.vnlauncher/logs", DisplayPaths.forDisplay("/home/julio/.vnlauncher/logs", Map.of(),
                "/home/julio"));
        assertEquals("/opt/juegos", DisplayPaths.forDisplay("/opt/juegos", Map.of(), "/home/julio"));
    }
}

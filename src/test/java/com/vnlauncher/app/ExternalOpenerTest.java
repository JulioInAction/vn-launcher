package com.vnlauncher.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExternalOpenerTest {

    @TempDir
    Path dir;

    @Test
    void webAddressesGoToTheBrowser() {
        List<String> opened = new ArrayList<>();

        ExternalOpener.open("https://vndb.org/v17", opened::add);

        assertEquals(List.of("https://vndb.org/v17"), opened);
    }

    @Test
    void aFolderNeverGoesToTheBrowser() {
        List<String> opened = new ArrayList<>();

        ExternalOpener.open(dir.toUri().toString(), opened::add);

        assertTrue(opened.isEmpty(), "Era el fallo: «Abrir carpeta» abría el navegador");
    }

    @Test
    void windowsOpensFoldersWithExplorerAndSelectsFiles() throws Exception {
        assertEquals(List.of("explorer.exe", dir.toString()), ExternalOpener.folderCommand("Windows 11", dir));
        Path file = Files.createFile(dir.resolve("juego.exe"));
        assertEquals(List.of("explorer.exe", "/select,", file.toString()),
                ExternalOpener.folderCommand("Windows 11", file));
    }

    @Test
    void otherSystemsUseTheirOwnOpener() {
        assertEquals(List.of("open", dir.toString()), ExternalOpener.folderCommand("Mac OS X", dir));
        assertEquals(List.of("xdg-open", dir.toString()), ExternalOpener.folderCommand("Linux", dir));
    }
}

package com.vnlauncher.service;

import com.vnlauncher.service.GameFiles.FileStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameFilesTest {

    @TempDir
    Path tempDir;

    private static FileStatus check(String path) {
        return GameFiles.check(path, Files::isRegularFile);
    }

    @Test
    void anExecutableThatIsThereIsFound() throws Exception {
        Path exe = Files.writeString(tempDir.resolve("RealLive.exe"), "");
        assertEquals(FileStatus.FOUND, check(exe.toString()));
    }

    @Test
    void anExecutableThatIsGoneIsMissing() {
        assertEquals(FileStatus.MISSING, check(tempDir.resolve("desinstalado/Juego.exe").toString()));
        assertEquals(FileStatus.MISSING, check(tempDir.toString()), "Una carpeta no es un ejecutable");
    }

    @Test
    void withoutAPathItIsNotMissingJustNotSet() {
        assertEquals(FileStatus.NOT_SET, check(null));
        assertEquals(FileStatus.NOT_SET, check("   "));
    }

    @Test
    void aPathThatCannotEvenBeReadIsMissing() {
        assertEquals(FileStatus.MISSING, check("D:\\Juegos\\mal\u0000nombre.exe"));
    }

    @Test
    void bothFilesOfAGame() {
        GameFiles files = new GameFiles(FileStatus.FOUND, FileStatus.MISSING);
        assertFalse(files.exeMissing());
        assertTrue(files.configMissing());
    }
}

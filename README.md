# VN Launcher

Launcher de escritorio para novelas visuales: datos de [VNDB](https://vndb.org), gestión de rutas,
lanzamiento (con Locale Emulator opcional) y registro de tiempo de juego.

## Requisitos

- JDK 26
- Maven 3.9+

## Comandos

```bash
mvn test          # ejecutar los tests
mvn javafx:run    # arrancar la aplicación
```

Los datos se guardan en `%LOCALAPPDATA%\VNLauncher` (`library.db` + carpeta `images`).

## Estado

- [x] Fase 0: esqueleto (ventana JavaFX + base de datos SQLite con migraciones)
- [x] Fase 1: cliente VNDB y búsqueda
- [x] Fase 2: biblioteca local
- [x] Fase 3: personajes
- [x] Fase 4: rutas
- [ ] Fase 5: lanzamiento
- [ ] Fase 6: tiempo de juego
- [ ] Fase 7: pulido

## Pendiente para la Fase 7

- Menú de **Opciones** con los ajustes que hagan falta, entre ellos:
  - desactivar el difuminado ("censura") de imágenes sensibles;
  - elegir/cambiar la ruta de Locale Emulator (`LEProc.exe`). En la Fase 5 se pedirá con un
    selector de archivos la primera vez que haga falta; en Opciones se podrá cambiar.
- Ejecutable secundario por VN (p. ej. `config.exe`).

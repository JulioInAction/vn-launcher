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
- [ ] Fase 1: cliente VNDB y búsqueda
- [ ] Fase 2: biblioteca local
- [ ] Fase 3: personajes
- [ ] Fase 4: rutas
- [ ] Fase 5: lanzamiento
- [ ] Fase 6: tiempo de juego
- [ ] Fase 7: pulido

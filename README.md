<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/dise%C3%B1o/logo/vn-launcher-logo-oscuro.svg">
  <img alt="VN Launcher" src="docs/dise%C3%B1o/logo/vn-launcher-logo-claro.svg" height="64">
</picture>

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

## Diseño

La guía visual está en [`docs/DISEÑO.md`](docs/DISE%C3%91O.md) y las capturas de referencia en
`docs/diseño/capturas/`. En el código:

- `src/main/resources/com/vnlauncher/ui/styles.css`: todos los colores son variables `-vn-*`
  en `.root` (tema oscuro) y `.root.tema-claro` (tema claro). El tema elegido se guarda en la
  tabla `setting` (clave `theme`).
- `src/main/resources/fonts/`: Inter y Zen Kaku Gothic New, con su licencia SIL OFL 1.1.

## Estado

- [x] Fase 0: esqueleto (ventana JavaFX + base de datos SQLite con migraciones)
- [x] Fase 1: cliente VNDB y búsqueda (rehecha con el diseño definitivo)
- [x] Fase 2: biblioteca local
- [x] Fase 3: personajes
- [x] Fase 4: rutas
- [x] Fase 5: lanzamiento (+ ficha de VN rediseñada)
- [x] Diseño: temas claro/oscuro, fuentes, barra lateral común, búsqueda nueva
- [x] Fase 6: tiempo de juego (+ biblioteca del diseño: Continuar, filtros, orden)
- [ ] Fase 7: pulido
- [ ] Fase 8: integraciones (Discord Rich Presence y tema del sistema)

## Cómo se mide el tiempo de juego

Al lanzar un juego, `PlaytimeTracker` comprueba cada 5 s si hay algún proceso ejecutándose
**desde la carpeta del juego** (da igual que el juego lance un proceso hijo y cierre el original,
o que pase por Locale Emulator). La sesión se guarda en `play_session`:

- Durante los primeros 30 s no se cierra aunque no se vea nada (el juego está arrancando).
- Si el juego desaparece menos de 15 s (p. ej. se reinicia), sigue siendo la misma sesión.
- Cada 30 s se guarda un "latido" (`last_seen_at`). Si el launcher se cierra de golpe, al volver a
  abrirlo la sesión se cierra con el último latido.
- Limitación: si cierras el launcher con el juego abierto, el tiempo posterior no se cuenta.

## Pendiente para la Fase 7

- Pantalla de **Ajustes** (entrada "03 Ajustes" de la barra lateral) con, entre otros:
  - desactivar el difuminado ("censura") de imágenes sensibles;
  - cambiar la ruta de Locale Emulator (`LEProc.exe`), que ahora se pide la primera vez que hace falta;
  - ver la carpeta de datos.
- Botón "Actualizar desde VNDB" en la ficha.
- Ejecutable secundario por VN (p. ej. `config.exe`).

## Plan de la Fase 8: integraciones

- **Discord Rich Presence**: mientras un juego está abierto (lo sabe el registro de tiempo de la
  Fase 6), mostrar en el perfil de Discord "Jugando a *VN* · *ruta en curso*" con el tiempo
  transcurrido y la portada.
  - Idea: hablar con Discord por su canal local (IPC; en Windows, la tubería con nombre
    `\\.\pipe\discord-ipc-0`) enviando mensajes JSON. Se puede hacer en Java puro, sin librerías
    nativas, y es un buen ejercicio de protocolos.
  - Hace falta crear una aplicación en el Discord Developer Portal para obtener su *client ID*
    (lo tendrá que hacer el usuario).
  - Opción en Ajustes para activarlo/desactivarlo; si Discord no está abierto, no hacer nada.
- **Tema del sistema** con [jSystemThemeDetector](https://github.com/Dansoftowner/jSystemThemeDetector):
  al arrancar, usar el tema (claro/oscuro) que tenga Windows.
  - Se publica en **JitPack** (`com.github.Dansoftowner:jSystemThemeDetector:3.9.1`), no en Maven
    Central: hay que añadir ese repositorio al `pom.xml`. Trae JNA, OSHI y slf4j como dependencias.
  - Propuesta: el ajuste `theme` pasa a tener tres valores, **Sistema** (por defecto), **Claro** y
    **Oscuro**. Con "Sistema" se sigue al de Windows, también si cambia con la app abierta (la
    librería avisa con un listener); el botón de la barra lateral fija Claro u Oscuro.

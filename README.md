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
  tabla `setting` (clave `theme`): `oscuro`, `claro` o `sistema` («Según Windows», que sigue el
  modo de color del sistema con `Platform.getPreferences()` de JavaFX, sin librerías extra).
- `src/main/resources/fonts/`: Inter y Zen Kaku Gothic New, con su licencia SIL OFL 1.1.
- Diálogos (`ModalDialog`), avisos (`Notice`), estados vacíos (`EmptyState`) y controles propios
  (`Segmented`, `Switch`) en `com.vnlauncher.ui`, todos con los colores del tema.

## Estado

- [x] Fase 0: esqueleto (ventana JavaFX + base de datos SQLite con migraciones)
- [x] Fase 1: cliente VNDB y búsqueda (rehecha con el diseño definitivo)
- [x] Fase 2: biblioteca local
- [x] Fase 3: personajes
- [x] Fase 4: rutas
- [x] Fase 5: lanzamiento (+ ficha de VN rediseñada)
- [x] Diseño: temas claro/oscuro, fuentes, barra lateral común, búsqueda nueva
- [x] Fase 6: tiempo de juego (+ biblioteca del diseño: Continuar, filtros, orden)
- [x] Diseño (2.ª parte): Ajustes, diálogo de ruta, diálogos de error al lanzar, estados vacíos,
  de carga y de error, tema «Según Windows»
- [ ] Fase 7: pulido
- [ ] Fase 8: Discord Rich Presence

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

Los Ajustes ya están hechos (Locale Emulator, tema, spoilers, nivel de difuminado, carpeta de
datos y caché). Queda:

- Botón «Actualizar desde VNDB» en la ficha (vuelve a pedir datos y personajes).
- Ejecutable secundario por VN (p. ej. `config.exe`).
- Empaquetar con `jpackage` para Windows, con el icono `vn-launcher.ico`.
- La tarjeta «Cuenta de VNDB» de Ajustes está desactivada («Próximamente»): servirá para
  sincronizar la lista de VNDB con un token, más adelante.

## Plan de la Fase 8: Discord Rich Presence

- Mientras un juego está abierto (lo sabe el registro de tiempo de la Fase 6), mostrar en el
  perfil de Discord "Jugando a *VN* · *ruta en curso*" con el tiempo transcurrido y la portada.
- Idea: hablar con Discord por su canal local (IPC; en Windows, la tubería con nombre
  `\\.\pipe\discord-ipc-0`) enviando mensajes JSON. Se puede hacer en Java puro, sin librerías
  nativas, y es un buen ejercicio de protocolos.
- Hace falta crear una aplicación en el Discord Developer Portal para obtener su *client ID*
  (lo tendrá que hacer el usuario).
- Opción en Ajustes para activarlo/desactivarlo; si Discord no está abierto, no hacer nada.
- El tema del sistema, que antes estaba en esta fase, ya está hecho con la API de JavaFX
  (`Platform.getPreferences().getColorScheme()`), así que no hace falta jSystemThemeDetector.

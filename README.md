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
mvn test                            # ejecutar los tests
mvn javafx:run                      # arrancar la aplicación
mvn clean package -P instalador     # crear el instalador (ver abajo)
```

Los datos se guardan en `%LOCALAPPDATA%\VNLauncher` (`library.db` + carpeta `images`). Desinstalar la
aplicación no los borra.

## Instalador para Windows

`mvn clean package -P instalador` deja en `target/instalador` un instalador
`VN Launcher-0.1.0.exe`. Lleva su propio Java reducido, así que en el PC donde se instale no hace
falta tener Java. Lo que hace el perfil (comentado en el `pom.xml`):

1. Copia el `.jar` de la aplicación y sus dependencias a `target/jpackage/mods`.
2. `jlink` crea un Java con solo los módulos del JDK que se usan (`target/jpackage/runtime`).
3. `jpackage` junta las dos cosas y crea el instalador con las opciones de
   `src/packaging/windows/instalador.txt`: menú Inicio, acceso directo en el escritorio (pregunta
   antes), elegir carpeta, instalación solo para tu usuario (sin permisos de administrador).

Necesita **WiX Toolset 3**, que es lo que usa `jpackage` para crear instaladores en Windows:

```bash
winget install --id WiXToolset.WiXToolset
```

Sin WiX se puede crear la versión **portable** (una carpeta con `VN Launcher.exe` que se copia donde
quieras):

```bash
mvn clean package -P instalador -Djpackage.options=src/packaging/windows/portable.txt
```

Notas:
- Antes de crear un instalador nuevo, sube la `<version>` del `pom.xml` (solo números, p. ej.
  `0.2.0`): Windows solo sustituye una instalación por otra de versión mayor. La misma versión sale
  en Ajustes → Acerca de.
- El instalador no está firmado, así que Windows SmartScreen avisará la primera vez
  («Más información» → «Ejecutar de todas formas»).
- El icono es `src/packaging/windows/vn-launcher.ico` (de 16 a 256 px), generado a partir de
  `vn-launcher-app.png`.

## Diseño

La guía visual está en [`docs/DISEÑO.md`](docs/DISE%C3%91O.md) y las capturas de referencia en
`docs/diseño/capturas/`. En el código:

- `src/main/resources/com/vnlauncher/ui/styles.css`: todos los colores son variables `-vn-*`
  en `.root` (tema oscuro) y `.root.tema-claro` (tema claro). El tema elegido se guarda en la
  tabla `setting` (clave `theme`): `oscuro`, `claro` o `sistema` («Según el sistema», que sigue el
  modo de color del sistema con `Platform.getPreferences()` de JavaFX, sin librerías extra).
- `src/main/resources/fonts/`: Inter y Zen Kaku Gothic New, con su licencia SIL OFL 1.1.
- Diálogos (`ModalDialog`), avisos (`Notice`), estados vacíos (`EmptyState`) y controles propios
  (`Segmented`, `Switch`) en `com.vnlauncher.ui`, todos con los colores del tema.
- **Texto nítido:** `.root` usa `-fx-font-smoothing-type: lcd`. JavaFX vuelve al suavizado gris si
  el texto (o un contenedor suyo) tiene un efecto (`-fx-effect`, desenfoque), `opacity` menor que 1
  o caché. Por eso los controles deshabilitados se apagan con colores y no con opacidad, y la
  sombra de los diálogos va en una región aparte, detrás de la tarjeta. Los `Text` sueltos (dentro
  de un `TextFlow`) no lo heredan: llevan la propiedad en su clase (`.text-strong`...).
  Además, las fuentes llevan *hinting* añadido con `tools/hint_fonts.py` (las de Google Fonts
  vienen sin él y en Windows se veían borrosas; ver `src/main/resources/fonts/README.md`).
  Y los `ScrollPane` usan `SharpScrollPaneSkin` (asignado en el CSS con `-fx-skin`): el skin normal
  guarda el contenido en caché y todo lo que había dentro (la ficha, la biblioteca...) salía en gris.
- Títulos y nombres originales (japonés): clase `.original-text`, con Zen Kaku Gothic New Medium.
- La ventana se abre la primera vez al 90 % de la pantalla y después recuerda su posición, tamaño
  y si estaba maximizada (ajuste `window`).
  Ancho mínimo: 1200 px (o el de la pantalla, si es menor), para que en Buscar quepan la lista y
  el panel de 540 px.

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
  de carga y de error, tema «Según el sistema»
- [x] Fase 7: pulido («Actualizar desde VNDB», programa de configuración, instalador)
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

## Búsqueda

- Se busca al pulsar Enter: **1 petición** a `/vn` que trae también los datos ligeros del panel
  (duración, valoración, idiomas, plataformas y estado de desarrollo).
- A la derecha, un panel fijo de 540 px con el resultado elegido: portada, título original, estado
  («Terminada», «En desarrollo», «Cancelada»), seis datos en dos columnas, las 5 etiquetas mejor
  puntuadas sin spoilers, 3 capturas (las sensibles, difuminadas) y la descripción recortada a 6
  líneas con «Leer más». El panel hace scroll por dentro y el botón de abajo siempre se ve.
- Las etiquetas, capturas y relaciones se piden **al seleccionar** un resultado (1 petición a `/vn`
  por su id, tras 250 ms sin cambiar de selección). Se guardan en una caché en memoria
  (`CatalogService.extras`): volver a elegir el mismo resultado no gasta otra petición.
- Las etiquetas de contenido sexual solo aparecen con «Difuminar imágenes: No difuminar» (VNDB
  también las oculta por defecto). De las relaciones se muestra la más importante, prefiriendo las
  oficiales («Fandisc de Fate/stay night», «Secuela de…»).
- Los nombres de idiomas y plataformas y los números («8,1 · 5.946 votos») se escriben a mano en
  `VnFacts`: el instalador no incluye los datos de idioma de Java (`jdk.localedata`).

## Ficha de una VN

- **Actualizar desde VNDB:** vuelve a pedir la ficha y los personajes principales (2 peticiones) y
  descarga la portada si ha cambiado. Las rutas, el tiempo de juego y el ejecutable no se tocan. El
  resto del tiempo la aplicación no vuelve a preguntar a VNDB por algo que ya tiene.
- **Cambiar portada:** bajo la portada hay una franja rosa que, al pasar el ratón por ella o al
  llegar con el tabulador, crece hasta un botón «Cambiar portada»
  (`CoverChanger`: una `Timeline` de 180 ms y un clip redondeado). Abre una galería
  (`CoverGallery`) con la portada de VNDB, las imágenes de cada edición (1 petición a `/release`;
  las miniaturas se descargan al abrirla y la imagen grande solo al confirmar) y «Elegir
  archivo…». La elegida se guarda en `library_entry.cover_override` (migración 004) y su archivo
  en la carpeta `covers/`, fuera de `images/` para que «Vaciar caché» no la borre. «Volver a la de
  VNDB» la pone a `NULL`. Se ve en la ficha, la biblioteca, «Continuar» y «Recientes».
- **Programa de configuración:** además del juego, cada VN puede tener su `config.exe` (o como se
  llame). Se abre con el botón «Configuración», desde su carpeta y con Locale Emulator si la VN lo
  usa. No cuenta como partida.

## Pendiente

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

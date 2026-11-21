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
- [ ] Versión 1.0 (en curso): registro de errores ✓, copias de seguridad automáticas ✓, restaurar
  una copia ✓, exportar e importar el perfil ✓, estado manual ✓, estanterías ✓, ejecutables perdidos ✓,
  vista previa de la ficha, búsqueda en la biblioteca, aviso de actualizaciones
- Después de la 1.0: GitHub Action que construya el instalador al subir una etiqueta; en la 1.1,
  traducción completa al inglés (ResourceBundle, selector de idioma, plurales con MessageFormat y
  fechas según el idioma). Por eso el código nuevo escribe cada frase como una plantilla completa
  (String.format o MessageFormat), sin pegar trozos de texto.

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

- Al salir de «Buscar» a otra pestaña, la búsqueda se descarta: al volver está vacía. Si había una
  búsqueda en marcha (p. ej. esperando al límite de VNDB), se cancela.
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

- **Cabecera simplificada** (`ficha-simplificada-*.png`). En la columna central, de arriba abajo:
  título, título original y «Desarrollador · año»; una fila de chips sin rótulos (el estado como
  pastilla «● Jugando · automático ⌄», las estanterías con ✕ y un «+» redondo); «Jugar» con dos
  botones cuadrados de 52 px, el engranaje («Opciones del juego») y «⋯» («Más opciones»: abrir el
  programa de configuración si hay uno, Ver en VNDB, Actualizar desde VNDB y Quitar de la
  biblioteca, en rojo); los datos en una línea («**37 min** jugados · última partida **hace 3 h** ·
  **0 de 4** rutas» con una barra de 80 × 5); las etiquetas que quepan en una línea (como mucho 5)
  con «+N más»; y la descripción recortada a 3 líneas con «Leer más» (recortada, los párrafos van
  seguidos para no gastar una línea en blanco). La ruta del ejecutable ya no se ve: va en el
  tooltip de «Jugar». Las negritas salen de plantillas con `<b>…</b>` (`RichText`), para que cada
  frase siga siendo una sola plantilla traducible.
- **Ejecutables perdidos** (`GameFiles`): en cada carga de la biblioteca (también al arrancar) y al
  abrir la ficha se mira en segundo plano si siguen ahí el ejecutable y el programa de
  configuración. Una ruta vacía no cuenta como perdida; una que no lleva a ningún archivo (o que ni
  siquiera es válida), sí. En la biblioteca, la portada lleva «ⓘ No encontrado» y el «Jugar» de
  «Continuar» se desactiva. En la ficha sale un aviso rojo entre los chips y las acciones («No se
  encuentra el ejecutable», la ruta y «Buscar el .exe») y «Jugar» se desactiva. Sin ejecutable
  elegido, el botón principal es «Elegir ejecutable». En «Opciones del juego», el campo de un
  archivo perdido va con borde rojo y «No se encuentra este archivo»; si el que falta es el
  programa de configuración, solo se marca ahí.
- **Opciones del juego** (`GameOptionsDialog`, `opciones-juego-*.png`): el ejecutable (ruta de solo
  lectura recortada por el principio, entera en el tooltip, y «Cambiar…»), el programa de
  configuración («Sin configurar» + «Elegir…», o la ruta con «Cambiar…» y «Quitar») y el interruptor
  de Locale Emulator. Cada cambio se guarda al momento; «Listo» solo cierra.
- **Actualizar desde VNDB:** desde el «⋯». Lo que pasa se ve a la derecha del «⋯», en pequeño y sin
  mover nada: «Actualizando…» con un indicador de carga (o «Esperando al límite de VNDB (1:42)…» con
  la cuenta atrás) y al terminar «✓ Actualizado» en azul, que se va solo a los 3 segundos. Los
  errores siguen saliendo en su aviso. Vuelve a pedir la ficha, los personajes principales y las etiquetas y
  capturas (3 peticiones) y descarga la portada si ha cambiado. Las rutas, el tiempo de juego y el ejecutable no se tocan. El
  resto del tiempo la aplicación no vuelve a preguntar a VNDB por algo que ya tiene.
- **Cambiar portada:** bajo la portada hay una franja rosa que, al pasar el ratón por ella o al
  llegar con el tabulador, crece hasta un botón «Cambiar portada»
  (`CoverChanger`: una `Timeline` de 180 ms y un clip redondeado). Abre una galería
  (`CoverGallery`) con la portada de VNDB, las imágenes de cada edición (1 petición a `/release`;
  las miniaturas se descargan al abrirla y la imagen grande solo al confirmar) y «Elegir
  archivo…». La elegida se guarda en `library_entry.cover_override` (migración 004) y su archivo
  en la carpeta `covers/`, fuera de `images/` para que «Vaciar caché» no la borre. «Volver a la de
  VNDB» la pone a `NULL`. Se ve en la ficha, la biblioteca y «Continuar».
- **Etiquetas:** la primera vez que se abre la ficha se piden a VNDB sus etiquetas y capturas
  (1 petición, como los personajes) y se guardan en `vn_tag` y `vn_screenshot` (migración 005), así
  que después funciona sin conexión. Se ven las 8 mejor puntuadas como chips (`TagChips`) y «+N más»
  despliega el resto (en la cabecera simplificada, solo las que quepan en una línea). Las spoiler solo con «Mostrar spoilers»; las de contenido sexual, solo con
  «Difuminar imágenes: No difuminar» (como en la búsqueda). Pulsar una abre la biblioteca filtrada
  por ella: arriba sale «Etiqueta: X ✕» y se combina con el filtro de estado. Solo cuenta las VNs
  cuya ficha se ha abierto alguna vez (las demás aún no tienen etiquetas guardadas; lo avisa).
  «‹ Biblioteca» vuelve a la biblioteca con los filtros que tenía.
- **Capturas:** galería en la cabecera (`ScreenshotGallery`): captura principal en 16:9 con
  contador y flechas (al final vuelve a la primera) y una fila de 6 miniaturas que se desplaza para
  que la elegida siempre se vea. Con la ventana ancha va como tercera columna (portada 220 px,
  información mínimo 400 px, galería entre 520 y 680 px); si no caben las tres, baja debajo de la
  cabecera. Un listener sobre el ancho la cambia de sitio solo al cruzar ese umbral. Las miniaturas
  se descargan al abrir la ficha y la imagen grande solo cuando se muestra (`ScreenshotService`,
  carpeta `images/shots/` de la caché; el nombre sale de la URL, así que no hace falta guardar la
  ruta en la base de datos). Las sensibles van difuminadas según «Difuminar imágenes». Sin capturas,
  no hay galería.
- **Visor de capturas:** clic en la captura principal (`ScreenshotViewer`): una capa encima de toda
  la ventana (el StackPane raíz), con fondo `#08090C` en los dos temas, la captura grande sin
  deformar, flechas, todas las miniaturas y las teclas ← → (dan la vuelta) y Esc. Las sensibles
  salen difuminadas con «Mostrar», y lo que se destapa sigue destapado en la ficha. Al cerrar, la
  galería se queda en la última captura vista. Carga la imagen grande al mostrarla y precarga la
  anterior y la siguiente (una caché pequeña en memoria, `ScreenshotSet`).
- **Difuminado barato:** las capturas sensibles no usan `GaussianBlur` sobre la imagen grande (sin
  tarjeta gráfica, p. ej. en una máquina virtual, eso tarda segundos por fotograma). Se difumina una
  vez una copia de 64 px con `BoxBlur` y se muestra ampliada.
- **Programa de configuración:** además del juego, cada VN puede tener su `config.exe` (o como se
  llame). Se elige en «Opciones del juego» y se abre desde el «⋯», desde su carpeta y con Locale
  Emulator si la VN lo usa. No cuenta como partida.
- **Estado:** cada VN tiene uno de cinco (`PlayStatus`): Jugando, Pendiente y Completada salen
  solos con sus rutas y partidas (`PlayStatus.of`); En pausa y Abandonada solo se fijan a mano. La
  pastilla de estado de la ficha (`StatusPicker`, un `ContextMenu`) ofrece «Automático (ahora:
  Jugando)» y los cinco. El fijado a mano va en `library_entry.status_override` (migración 006,
  `NULL` = automático) y manda aunque se juegue, hasta volver a «Automático». Cada estado tiene su
  equivalente en las listas de VNDB (`vndbLabelId`: Playing 1, Finished 2, Stalled 3, Dropped 4;
  Pendiente, ninguna) para cuando se sincronice con la cuenta.
- **Biblioteca y barra lateral:** el bloque «Estado» de la barra cuenta las VNs de cada uno y filtra
  al pulsarlo (resaltado mientras filtra). En la biblioteca, encima de la cuadrícula, los filtros
  activos («Estado: Jugando ✕», «Etiqueta: Drama ✕») se combinan y se quitan con su ✕.
  «Biblioteca» enseña todo sin filtros. «Recientes» ya no está: la última jugada sale en
  «Continuar». La navegación de la barra va sin números.
- **Estanterías** (`ShelfService`, migración 007: tablas `shelf` y `shelf_entry`): listas propias
  («Favoritas», «Para la Steam Deck»...) con uno de 8 colores, guardado como clave (`rosa`...) y
  pintado con los colores `-vn-shelf-*` del tema. Una VN puede estar en varias. En la barra, «Mis
  estanterías» con su contador: se crean con «+», se reordenan arrastrando y con clic derecho →
  «Editar…» (`ShelfDialog`) se cambian nombre y color, se mueven con «Subir»/«Bajar» o se eliminan
  (las novelas no se borran). Nombres de 1 a 40 caracteres y sin repetir, sin distinguir
  mayúsculas ni con acentos (`ShelfService.nameProblem`, que el diálogo usa mientras se escribe; el
  índice `UNIQUE ... COLLATE NOCASE` lo respalda). En la ficha, en la fila de chips, detrás del
  estado (`ShelfChips`), con un «+» redondo para añadir. Al quitar una VN de la biblioteca sale sola de sus
  estanterías (`ON DELETE CASCADE`).
- **Filtros** (`LibraryQuery`, sin nada de interfaz y con tests): un estado **o** una estantería
  (elegir uno quita el otro), más la etiqueta y un texto de búsqueda (título, título original o
  desarrollador; la caja de búsqueda llega en la fase 11), que se combinan con cualquiera.

## Registro de errores

- La app escribe un registro en `logs/` dentro de la carpeta de datos (`%LOCALAPPDATA%\VNLauncher`):
  `vn-launcher0.log` es el actual y `vn-launcher1.log` … `4.log` los anteriores, de 1 MB como
  mucho cada uno (`FileHandler` de `java.util.logging`, `LogSetup`). Al arrancar apunta la versión
  de la app, de Java y del sistema, y la versión del esquema de la base de datos.
- Los errores inesperados (un fallo de la base de datos, de programación...) se registran y abren
  el diálogo «Algo ha fallado», con los detalles técnicos desplegables, «Copiar detalles» y «Abrir
  carpeta de registros» (`ErrorReporter`, `ErrorDialog`). Los esperados (sin conexión, límite de
  VNDB) siguen con sus avisos dentro de cada pantalla.
- Llegan al diálogo: las excepciones que nadie captura en cualquier hilo (manejador global), las
  tareas en segundo plano sin manejador propio (`BackgroundTasks` con mensaje) y los errores que
  cada pantalla reporta con su contexto («No se ha podido guardar la ruta.»).
- Las rutas se enseñan como `%LOCALAPPDATA%\...` (`DisplayPaths`): al pegar los detalles en un
  informe no se comparte el nombre de la cuenta de Windows.
- En Ajustes → Registro de errores: «Abrir carpeta de registros» y «Copiar información del sistema».
- Para probarlo sin romper nada: `-Dvnlauncher.debug.fallo=arranque|fx|tarea|hilo` provoca un error
  a propósito (al preparar la base de datos, en el hilo de la interfaz, en una tarea en segundo
  plano o en un hilo suelto). `-Dvnlauncher.log.level=FINE` escribe también los mensajes de detalle.

## Copias de seguridad

- Copias automáticas de la base de datos en `backups/` dentro de la carpeta de datos
  (`BackupService`): antes de aplicar migraciones nuevas (al arrancar una versión nueva de la app),
  una cada semana, antes de restaurar una copia y antes de importar un perfil.
  Se guardan las 5 últimas, de todos los tipos juntos.
- Se hacen con `VACUUM INTO`: SQLite escribe una base nueva y compacta con el contenido de ese
  momento (también lo que aún está en el WAL) sin bloquear la app. Se escribe con otro nombre
  (`.part`) y se renombra al acabar, así una copia a medias nunca parece buena.
- El nombre lo dice todo: `vn-launcher-2026-11-21_104203-actualizacion-1.0.0.db` (fecha, tipo y
  versión). No hace falta ningún índice aparte. Los archivos con otro nombre no se tocan.
- Si la copia de antes de actualizar no se puede hacer (disco lleno, sin permisos...), la app se
  actualiza igualmente (las migraciones van en una transacción), lo apunta en el registro y, al abrir
  la ventana, avisa: «No se ha podido hacer la copia de seguridad antes de actualizar. Revisa que
  quede espacio libre en el disco».
- La semanal se hace en segundo plano al abrir la app si la última tiene más de 7 días. En Ajustes
  → Perfil y copias de seguridad se puede apagar (las de antes de actualizar, importar o restaurar
  se hacen siempre), se ven las copias que hay y se abre su carpeta.
- Fechas en la interfaz con un patrón numérico fijo (`dd/MM/yyyy, HH:mm`, en `Formats`): no
  dependen de los datos de idioma de Java, que el instalador no incluye.

### Restaurar una copia

«Restaurar» en cada fila de la lista. La app **se reinicia** para aplicarla, en vez de cambiar la
base de datos con todo abierto (conexiones, tareas en segundo plano, pantallas con datos viejos):
al arrancar todavía no hay nada abierto, y el cambio es solo mover archivos.

1. Con la app abierta (`RestoreService.prepare`): no se deja si hay un juego abierto (su tiempo de
   juego se guardaría en la base que se va a sustituir); se comprueba la copia abriéndola en solo
   lectura (`PRAGMA integrity_check`, `PRAGMA foreign_key_check` y que su esquema no sea más nuevo
   que el de la app); tras confirmar, se aparta en `pendiente/library.db` (antes de nada: la copia
   «Antes de restaurar una copia» puede borrar la más vieja, que podría ser la elegida), se hace
   esa copia y se apunta la operación en `operacion-pendiente.properties`.
2. Reinicio (`Relauncher`): se abre un proceso nuevo con `--esperar-pid=<pid>` y este se cierra. El
   nuevo espera a que el viejo termine. Instalada, la app se vuelve a abrir con su `.exe` (jpackage
   deja la ruta en la propiedad `jpackage.app-path`); en desarrollo, con el mismo `java` y los
   mismos argumentos. Si no se puede, se avisa de que hay que abrirla a mano: lo pendiente se
   termina igualmente en el siguiente arranque.
3. Al arrancar, antes de abrir la base (`PendingOperations.applyPending`): `library.db` se aparta
   como `library.db.anterior` (con su `-wal`, que tiene cambios de la base vieja y estropearía la
   nueva), se pone la copia y se comprueba que SQLite la lee. Si algo falla, vuelve la de antes. Se
   intenta una sola vez, y al abrir la ventana se avisa de cómo ha ido («Copia restaurada» o el
   diálogo de error). Si la copia es de una versión anterior, las migraciones la ponen al día.
4. Los ajustes que son de este ordenador se quedan: la posición y el tamaño de la ventana, siempre;
   la ruta de LEProc.exe de los datos nuevos solo si existe aquí (si no existe y la de aquí sí, se
   queda la de aquí). El resto de ajustes viene con los datos nuevos.

### Exportar e importar el perfil

Para llevarse la biblioteca a otro ordenador (o guardarla aparte). En Ajustes → Perfil y copias
de seguridad (`ProfileService`):

- **Exportar** escribe un `.zip` (`vn-launcher-perfil-2026-11-21.zip`) con `manifest.json` (versión
  del formato y de la app, esquema, fecha y recuentos: novelas, rutas, tiempo de juego, portadas),
  `library.db` (copia con `VACUUM INTO`, aunque la app esté en uso) y `covers/` (las portadas
  propias). La caché de imágenes no va: se vuelve a descargar de VNDB. Se escribe en un `.part`
  que se renombra al terminar.
- **Importar** usa el mismo mecanismo que restaurar: no se deja con un juego abierto; el zip se
  descomprime en `pendiente/` y se comprueba **antes** de preguntar; el diálogo «Importar perfil»
  (`ImportProfileDialog`) enseña el resumen y avisa de que sustituye al perfil actual; al
  confirmar se hace la copia «Antes de importar un perfil» y se reinicia. Al arrancar se pone la
  base, se aplican las reglas de ajustes de este ordenador de arriba y las portadas del perfil se
  **suman** a las de `covers/` (nunca se borra ninguna; si coincide el nombre es la misma imagen).
- El zip puede venir de cualquier sitio, así que no se fía de él: se lee con `ZipFile` (el índice
  del final, lo mismo que ve cualquier programa), solo se extraen `manifest.json`, `library.db` y
  `covers/<nombre>` con nombres de portada (letras, números, `.`, `_` y `-`); un nombre absoluto,
  con `..`, `\` o `:` rechaza el perfil entero, y además se comprueba que el destino queda dentro
  de `pendiente/`. Se cuentan los bytes de verdad al descomprimir (como mucho 2 GB) para parar un
  zip que se infla a lo bestia. La base se comprueba entera (integridad y claves foráneas) y ni el
  formato ni el esquema pueden ser más nuevos que los de la app; uno más viejo se migra al arrancar.
- Los campos que no conoce del `manifest.json` se ignoran, así una versión futura puede añadir
  cosas sin romper las anteriores.

**Una sola ventana a la vez** (`SingleInstance`): al arrancar se coge un bloqueo del sistema sobre
`vn-launcher.lock` (`FileChannel.tryLock`). Si otra VN Launcher lo tiene, sale «VN Launcher ya está
abierto» y esta se cierra. Si la app se cierra de golpe, el sistema suelta el bloqueo solo.

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

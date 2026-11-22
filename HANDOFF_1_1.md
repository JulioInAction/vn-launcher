# Traspaso: VN Launcher camino de la 1.0 y la 1.1

Estado a 22/11/2026. Rama `claude/sharp-goodall-doj9gv` (fase 10 sin fusionar en `main`). 266 tests en verde
(`mvn -q -B test`; en el entorno de pruebas Linux: `JAVA_HOME=…java-25… -Dmaven.compiler.release=25`).

## 1. Hecho y verificado

Todo probado con tests y en la app real (Linux, Xvfb). **Falta una pasada en Windows** (ver paso 5).

| Fase | Qué |
|---|---|
| 1 | Registro de errores (`logs/vn-launcher0.log`), diálogo «Algo ha fallado» con detalles técnicos, manejador global |
| 2 | Copias automáticas (`backups/`): antes de migrar, semanal (desactivable), antes de restaurar/importar; se guardan 5 |
| 3 | Restaurar una copia **reiniciando la app**; una sola instancia (`vn-launcher.lock`) |
| 4 | Exportar/importar perfil (`.zip`: `manifest.json` + `library.db` + `covers/`), diálogo «Importar perfil» |
| 5 | Estado manual (migración 006): 5 estados, bloque «Estado» en la barra, filtros activos con ✕, selector en la ficha |
| 6 | Estanterías (migración 007): barra «Mis estanterías» (arrastrar, clic derecho → Editar), diálogo, chips en la ficha; navegación sin números |
| — | Ficha simplificada (`ficha-simplificada-*`) + diálogo «Opciones del juego» (`opciones-juego-*`) |
| 7 | Ejecutables perdidos: insignia «No encontrado», aviso rojo en la ficha, campo marcado en Opciones |
| 8 | Quitar una VN borra su portada propia de `covers/` |
| 9 | Menú de clic derecho en las portadas (Jugar, Abrir configuración, Abrir carpeta, Estantería, Estado, Quitar) |
| 10 | Vista previa de la ficha (migración 008): «Ver ficha» / doble clic en Buscar, tarjetas, «Añadir» en el sitio, limpieza de la caché a los 30 días |
| 11 | Buscador en la biblioteca (título, original, desarrollador; sin mayúsculas, tildes ni ancho completo; Ctrl+F y Esc), botón del ojo de 44 px (solo la sesión) y «Ordenar por» recordado (`library.sort`, viaja con el perfil; si no se reconoce, «Último jugado») |
| 12 | Aviso de actualizaciones (`UpdateService`, solo en Ajustes → Acerca de): recuadro azul «Hay una versión nueva» con «Ver novedades» (abre la release en el navegador), línea «Tienes la última versión · comprobado hoy a las 18:07» o «No se ha podido comprobar…», «Comprobar ahora», interruptor «Buscar actualizaciones al arrancar» (`updates.on_start`) y punto de 8 px junto a «Ajustes» (`updates.notified`) |
| — | Retoques: filas de rutas (centrado, píldora única, lápiz), línea de datos sin cortes, «+» centrado |

## 2. Archivos relevantes (`src/main/java/com/vnlauncher/`)

- **app/** `Main` (orden de arranque), `AppContext`, `SingleInstance`, `Relauncher`, `LogSetup`, `SystemInfo`
- **db/** `Database` (`inspect`, `backupTo`, lista `MIGRATIONS`), `repo/LibraryRepository`, `repo/ShelfRepository`;
  `resources/.../db/migration/006_library_status_override.sql`, `007_shelves.sql`
- **service/** `UpdateService`, `PreviewService`, `CacheCleanupService`, `BackupService`, `RestoreService`, `PendingOperation(s)`, `ProfileService`, `ShelfService`,
  `LibraryQuery`, `GameFiles`, `LibraryService`, `CoverService`
- **model/** `PlayStatus`, `LibraryEntry`, `LibraryItem`, `Shelf`, `ShelfColor`, `ProfileManifest`, `BackupKind`
- **ui/** `ErrorReporter`, `ErrorDialog`, `StartupNotices`, `SettingsController`, `ImportProfileDialog`,
  `SidebarController`, `LibraryController`, `LibraryTileMenu`, `LibraryRemoval`, `VnDetailController`,
  `GameOptionsDialog`, `DetailLayout`, `StatusPicker`, `StatusVisuals`, `ShelfChips`, `ShelfDialog`, `TagChips`, `RichText`,
  `Icons`, `RoutesPane`, `CoverView`, `ModalDialog`
- **util/** `AppPaths`, `Formats`, `DisplayPaths`, `ErrorDetails`
- **recursos:** `styles.css`, `vn-detail-view.fxml`, `sidebar.fxml`, `library-view.fxml`, `settings-view.fxml`
- **docs:** `README.md`, `docs/DISEÑO.md`

## 3. Decisiones cerradas (no romper)

**Datos**
- Las migraciones publicadas no se tocan: solo se añaden. Antes de migrar se hace la copia «Antes de actualizar».
- Copias con `VACUUM INTO`, escritas en `.part` y renombradas con `ATOMIC_MOVE`. El nombre lo dice todo; se guardan 5.
- Restaurar e importar **nunca en caliente**: se prepara en `pendiente/` + `operacion-pendiente.properties`, se
  reinicia (`--esperar-pid`) y `PendingOperations.applyPending` cambia la base **antes** de abrirla.
  Un solo intento, con vuelta atrás, y el `-wal` viejo se aparta.
- La copia elegida se aparta **antes** de hacer la copia «Antes de restaurar» (la retención podría borrarla).
- El bloqueo de instancia única se coge antes de preparar el registro.
- Ajustes de este ordenador al restaurar/importar: la ventana, siempre la local; LEProc.exe del perfil
  solo si existe aquí; el resto viene del perfil.
- Un zip importado no es de fiar:
  - solo nombres conocidos y nada fuera de `pendiente/`;
  - como mucho 2 GB, contando los bytes reales;
  - `integrity_check` + `foreign_key_check`;
  - esquema y formato no más nuevos que la app;
  - los campos desconocidos del manifest se ignoran.
- La portada propia solo se borra si está de verdad dentro de `covers/` (`CoverService.customCoverFile`).
- Ruta vacía ≠ ejecutable perdido. Las comprobaciones de disco van fuera del hilo de la interfaz.

**Estados, estanterías y filtros**
- Estados: los automáticos son Jugando, Pendiente y Completada. En pausa y Abandonada son solo manuales.
- Lo fijado a mano manda hasta elegir «Automático».
- Listas de VNDB: Playing 1, Finished 2, Stalled 3, Dropped 4; Pendiente, ninguna.
- Estanterías:
  - el color se guarda como una de 8 claves (`rosa`…) y se pinta con `-vn-shelf-*`;
  - nombre de 1 a 40 caracteres, único sin distinguir mayúsculas ni tildes.
- Filtros (`LibraryQuery`, sin interfaz): estado **o** estantería; la etiqueta y el texto se combinan con
  cualquiera. «Biblioteca» los limpia todos.
- Sin ejecutable, el botón principal es «Elegir ejecutable».

**Interfaz y código**
- Colores solo con tokens `-vn-*` definidos en `.root` y `.root.tema-claro`. Iconos dibujados con `SVGPath`
  (`Icons`), nunca caracteres de texto.
- i18n: frases completas con `String.format`/`MessageFormat` (plurales con `choice`), sin concatenar trozos.
  Las negritas, con plantillas `<b>…</b>` (`RichText`).
- Fechas `dd/MM/yyyy, HH:mm` (`Formats`), sin `jdk.localedata`.
- Errores:
  - los inesperados, con `ErrorReporter`;
  - los esperados (sin red, VNDB, al lanzar), con aviso en pantalla;
  - `BackgroundTasks.runAndHandle` cuando quien llama informa, para no registrar dos veces;
  - los diálogos se abren con `Platform.runLater`.
- Búsqueda: se descarta al cambiar de pestaña; si vienes de Buscar, vuelves a Buscar.
- Commits:
  - uno por tarea, con push;
  - terminan con las líneas `Co-Authored-By` y `Claude-Session`;
  - sin identificadores de modelo.

## 4. Siguientes pasos (en orden)

1. ~~**Fase 10 · Vista previa de la ficha**~~ — hecha. Notas para quien siga:
   - `VnDetailController` tiene dos modos (`DetailLayout.Mode`): `PREVIEW` y `LIBRARY`. Lo que se ve en cada uno lo decide `DetailLayout` (con tests); al «Añadir» la misma ficha pasa a `LIBRARY` en el sitio.
   - La búsqueda **no se descarta** al abrir una ficha desde ella (`Navigator.showVnPreview` / `showVnDetailFromSearch`); «‹ Buscar» (`backToSearch`) vuelve intacta y `SearchController.libraryChanged()` refresca «✓ En tu biblioteca».
   - Etiquetas y capturas se guardan **enteras** (con spoilers y todas las capturas) desde `CatalogService.cachedDetails`, sin otra petición, al «Ver ficha» o al añadir (`PreviewService.remember`). Mientras se navega por los resultados no se escribe nada.
   - Migración 008: `vn.last_viewed_at` (rellenada con `fetched_at`). `CacheCleanupService` corre al arrancar (`startBackgroundMaintenance`): borra VNs fuera de la biblioteca con 30 días sin verse, sus personajes y desarrolladores huérfanos y sus imágenes; nunca un personaje con ruta. Quitar una VN de la biblioteca cuenta como «vista».
   - Diferencia con la imagen del diseño: «Ver en VNDB» va solo en el «⋯» (como pide este traspaso), no también como enlace junto al año.
2. ~~Recordar «Ordenar por»~~ y 3. ~~**Fase 11**~~ — hechos (ver tabla). El texto del buscador es parte de `LibraryQuery`; «Biblioteca» y pulsar una etiqueta lo vacían, y un estado o estantería lo conservan.
4. ~~**Fase 12 · Aviso de actualizaciones**~~ — hecha. Notas:
   - Última release de GitHub (`/releases/latest` de `JulioInAction/vn-launcher`), comparación numérica por partes, ignora la «v» y los sufijos. Sin red o error → «No se ha podido comprobar…» sin color de error; un 404 (aún no hay releases) cuenta como «al día». Solo se abre un enlace de nuestro repositorio; si no, se usa el de la etiqueta.
   - El punto de la barra lateral sale al arrancar si hay versión nueva y no se avisó de ella; entrar en Ajustes guarda `updates.notified` y no vuelve hasta que salga otra.
   - Para probar a mano: `-Dvnlauncher.updates.url=http://127.0.0.1:8765/x` apunta la comprobación a un servidor local.
5. **Pasada en Windows:**
   - reinicio con el `.exe` instalado (restaurar e importar);
   - instancia única;
   - exportar/importar;
   - «Abrir carpeta»;
   - aspecto de menús y diálogos (aquí salen esquinas negras y borde blanco por Xvfb).
6. **Fase 13 · Publicar la 1.0.0.** Versión 1.0.0 (ahora 0.1.0), licencia MIT, `CHANGELOG.md`, README, prueba instalando encima de la 0.1.0 y release a mano (`v1.0.0`).
7. **Después de la 1.0:**
   - GitHub Action que construya el instalador al subir la etiqueta y publique junto a él su SHA-256 (p. ej. `VN-Launcher-1.0.1.exe.sha256`).
   - **Botón «Descargar e instalar»** (en el recuadro azul de Ajustes → Acerca de, una vez exista la Action): baja el `.exe` de la release a una carpeta temporal con barra de progreso, comprueba su SHA-256 contra el publicado por la Action, lo lanza y cierra la app. Si el hash no coincide, borrar el archivo y avisar sin ejecutar nada. `UpdateService` ya devuelve la release; habrá que leer también sus `assets` (URL del `.exe` y del `.sha256`). Hasta entonces «Ver novedades» solo abre la página.
8. **1.1:**
   - traducción completa al inglés (ResourceBundle, selector de idioma, `MessageFormat`, fechas según el idioma);
   - deslizador del tamaño de las portadas: en tiempo real y con Ctrl+rueda. Cada portada se carga una vez a 260 px × escala con suavizado y la reduce el `ImageView`. Si va a tirones, avisar antes de cambiar de enfoque.
9. **1.x (por decidir):** Discord Rich Presence. Hay que mover su plan del README a lo pendiente.

**Opcional:** el «+» de «MIS ESTANTERÍAS» en la barra lateral sigue siendo texto; se puede pasar a `Icons.plus()`.

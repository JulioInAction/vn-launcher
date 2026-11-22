# Traspaso: VN Launcher camino de la 1.0 y la 1.1

Estado a 22/11/2026. Rama `main`. 236 tests en verde
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
| — | Retoques: filas de rutas (centrado, píldora única, lápiz), línea de datos sin cortes, «+» centrado |

## 2. Archivos relevantes (`src/main/java/com/vnlauncher/`)

- **app/** `Main` (orden de arranque), `AppContext`, `SingleInstance`, `Relauncher`, `LogSetup`, `SystemInfo`
- **db/** `Database` (`inspect`, `backupTo`, lista `MIGRATIONS`), `repo/LibraryRepository`, `repo/ShelfRepository`;
  `resources/.../db/migration/006_library_status_override.sql`, `007_shelves.sql`
- **service/** `BackupService`, `RestoreService`, `PendingOperation(s)`, `ProfileService`, `ShelfService`,
  `LibraryQuery`, `GameFiles`, `LibraryService`, `CoverService`
- **model/** `PlayStatus`, `LibraryEntry`, `LibraryItem`, `Shelf`, `ShelfColor`, `ProfileManifest`, `BackupKind`
- **ui/** `ErrorReporter`, `ErrorDialog`, `StartupNotices`, `SettingsController`, `ImportProfileDialog`,
  `SidebarController`, `LibraryController`, `LibraryTileMenu`, `LibraryRemoval`, `VnDetailController`,
  `GameOptionsDialog`, `StatusPicker`, `StatusVisuals`, `ShelfChips`, `ShelfDialog`, `TagChips`, `RichText`,
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

1. **Fase 10 · Vista previa de la ficha (migración 008).** Diseño: `ficha-vista-previa-*.png`.
   - **Buscar:** «Añadir a la biblioteca» + «Ver ficha», y doble clic en un resultado. «‹ Buscar» vuelve a la búsqueda intacta.
   - **Etiquetas y capturas de la búsqueda:** se quedan en memoria y solo se guardan al «Ver ficha» o al añadir.
   - **Lo que no sale en la vista previa:** chips de estado y estanterías, engranaje, rutas, cambiar portada y «Actualizar desde VNDB». El «⋯» solo tiene «Ver en VNDB».
   - **Tarjetas Duración, Valoración e Idiomas:** solo en la vista previa, en el sitio de la línea de datos.
     - Una fila compacta: etiqueta en `-vn-muted` arriba, valor debajo, fondo `-vn-surface`, sin iconos.
     - Si falta un dato, su tarjeta no sale; sin ninguno, no hay fila.
     - Al añadir la novela, las tarjetas pasan a la línea normal, en el sitio.
   - **Migración 008:** `vn.last_viewed_at` y limpieza a los 30 días de las VNs que no están en la biblioteca. Nunca borra personajes con ruta.
   - **Clases nuevas:** `ui.DetailLayout` (sin interfaz, con tests) y `service.CacheCleanupService`.
2. **Recordar «Ordenar por»** en `setting`. Viaja con el perfil; si el valor no se reconoce, «Último jugado». Hacerlo junto a la fase 11.
3. **Fase 11 · Buscador en la biblioteca y botón del ojo.**
   - Buscador: título, título original japonés y desarrollador. Ignora mayúsculas, tildes y ancho completo. Ctrl+F lo enfoca y Esc lo vacía.
   - Ojo: `ToggleButton` de 44 px que sustituye a la casilla «Mostrar imágenes sensibles». Solo dura la sesión y no toca el ajuste.
4. **Fase 12 · Aviso de actualizaciones.** Última release de GitHub, comparación numérica por partes (1.0.10 > 1.0.9), ignora la «v». Falla en silencio sin red; aviso e interruptor en Ajustes.
5. **Pasada en Windows:**
   - reinicio con el `.exe` instalado (restaurar e importar);
   - instancia única;
   - exportar/importar;
   - «Abrir carpeta»;
   - aspecto de menús y diálogos (aquí salen esquinas negras y borde blanco por Xvfb).
6. **Fase 13 · Publicar la 1.0.0.** Versión 1.0.0 (ahora 0.1.0), licencia MIT, `CHANGELOG.md`, README, prueba instalando encima de la 0.1.0 y release a mano (`v1.0.0`).
7. **Después de la 1.0:**
   - GitHub Action que construya el instalador al subir la etiqueta.
8. **1.1:**
   - traducción completa al inglés (ResourceBundle, selector de idioma, `MessageFormat`, fechas según el idioma);
   - deslizador del tamaño de las portadas: en tiempo real y con Ctrl+rueda. Cada portada se carga una vez a 260 px × escala con suavizado y la reduce el `ImageView`. Si va a tirones, avisar antes de cambiar de enfoque.
9. **1.x (por decidir):** Discord Rich Presence. Hay que mover su plan del README a lo pendiente.

**Opcional:** el «+» de «MIS ESTANTERÍAS» en la barra lateral sigue siendo texto; se puede pasar a `Icons.plus()`.

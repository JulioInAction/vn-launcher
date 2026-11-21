# Diseño de la interfaz — VN Launcher

Guía visual para la interfaz JavaFX. Las capturas de `docs/diseno/capturas/` son la referencia de cómo debe quedar cada pantalla; este documento da los valores exactos.

## Pantallas

| Captura | Pantalla | Fase |
|---|---|---|
| `biblioteca-oscuro.png` / `biblioteca-claro.png` | Biblioteca: franja «Continuar», filtros por estado, cuadrícula de portadas | 2 |
| `ficha-oscuro.png` / `ficha-claro.png` | Ficha de la VN: datos, Jugar, rutas en orden, personajes «primary» | 2, 3 y 4 |
| `ficha-capturas-derecha-oscuro.png` / `-claro.png` | Ficha con etiquetas (chips, «+N más») y capturas a la derecha | Ficha: etiquetas y galería de capturas (hecho) |
| `ficha-capturas-estrecha-oscuro.png` | Ficha con la ventana estrecha: la galería baja debajo de la cabecera | Hecho |
| `visor-capturas.png` | Visor de capturas a pantalla completa | Hecho |
| `buscar-oscuro.png` / `buscar-claro.png` | Búsqueda en VNDB con vista previa del resultado | 1 (rehacer al aplicar el diseño) |
| `ajustes.png` | Ajustes: Locale Emulator, tema, contenido, datos, cuenta de VNDB, acerca de | 5 |
| `dialogo-ruta.png` | Diálogo para crear o editar una ruta | 4 |
| `vacio-biblioteca.png`, `vacio-busqueda.png`, `vacio-personajes.png` | Estados vacíos | 2, 1 y 3 |
| `cargando-portadas.png` | Carga de portadas con marcadores de posición | 2 |
| `error-sin-conexion.png`, `error-limite.png` | Avisos de la búsqueda: sin conexión y límite de 200 peticiones | 1 |
| `error-ejecutable.png`, `error-locale-emulator.png` | Diálogos al lanzar: falta el .exe o falta LEProc.exe | 5 |

## Temas y colores

Hay dos temas, **oscuro** (por defecto) y **claro**, más la opción «Según el sistema». El usuario lo cambia en Ajustes → Apariencia y se guarda en la tabla `setting` (clave `theme`).

El tema claro es cálido y sin blancos puros: fondo `#ECE6DF`, barra lateral `#F2EDE7` y tarjetas `#F6F2ED`, con bordes y huecos de imagen en la misma gama de beige. Así las tarjetas se distinguen del fondo sin deslumbrar.

En JavaFX los colores se definen una sola vez como *looked-up colors* en `.root`, y el tema claro los sobrescribe con una clase de estilo:

```css
.root { -vn-bg: #121318; /* ... */ }
.root.tema-claro { -vn-bg: #ECE6DF; /* ... */ }
.tarjeta { -fx-background-color: -vn-surface; }
```

Cambiar de tema = añadir o quitar `tema-claro` de `scene.getRoot().getStyleClass()`. Ningún control lleva colores escritos a mano: todos usan estas variables.

| Variable | Uso | Oscuro | Claro |
|---|---|---|---|
| `-vn-bg` | Fondo de la ventana | `#121318` | `#ECE6DF` |
| `-vn-side` | Barra lateral | `#16171D` | `#F2EDE7` |
| `-vn-surface` | Tarjetas y paneles | `#1F2129` | `#F6F2ED` |
| `-vn-surface2` | Fondo secundario (huecos de imagen) | `#282B35` | `#E6DFD7` |
| `-vn-sel` | Fila seleccionada | `#262833` | `#F5E6EA` |
| `-vn-border` | Bordes suaves | `#2A2D37` | `#DCD3CA` |
| `-vn-border2` | Bordes de botones e inputs | `#3A3D49` | `#C9BFB5` |
| `-vn-text` | Texto principal | `#ECEDF2` | `#1D1B22` |
| `-vn-text2` | Texto secundario | `#C9CCD6` | `#3F3C47` |
| `-vn-muted` | Texto apagado (metadatos) | `#A4A8B6` | `#5E5B68` |
| `-vn-faint` | Decorativo (números de orden, asas) | `#6E7280` | `#7D7A86` |
| `-vn-accent` | Rosa: fondo del botón principal y marcapáginas | `#F2A0B5` | `#F2A0B5` |
| `-vn-accent-ink` | Rosa para texto y enlaces | `#F2A0B5` | `#B0365E` |
| `-vn-on-accent` | Texto sobre el botón rosa | `#2A1219` | `#2A1219` |
| `-vn-blue` | Estado Completada / Tiene ruta | `#8CC8FF` | `#1E66AD` |
| `-vn-blue-bg` | Fondo de la etiqueta azul | `rgba(140,200,255,0.14)` | `rgba(30,102,173,0.10)` |
| `-vn-orange` | Estado Jugando / En curso | `#F3B76B` | `#9A5600` |
| `-vn-orange-bg` | Fondo de la etiqueta naranja | `rgba(243,183,107,0.14)` | `rgba(196,122,20,0.13)` |
| `-vn-grey` | Estado Pendiente | `#B4B8C5` | `#5E5B68` |
| `-vn-text2` | También el aro de En pausa y el texto de su etiqueta (fondo `-vn-grey-bg`) | `#C9CCD6` | `#3F3C47` |
| `-vn-danger` | También el estado Abandonada (punto y etiqueta, con fondo `-vn-danger-bg`) | `#FF8F85` | `#B3261E` |
| `-vn-grey-bg` | Fondo de la etiqueta gris | `rgba(164,168,182,0.14)` | `rgba(94,91,104,0.10)` |
| `-vn-chip-on-bg` | Filtro activo (fondo) | `#ECEDF2` | `#1D1B22` |
| `-vn-chip-on-text` | Filtro activo (texto) | `#121318` | `#FFFFFF` |
| `-vn-track` | Fondo de barras de progreso | `#30333E` | `#DCD3CA` |
| `-vn-watermark` | Texto japonés de fondo en la barra lateral | `rgba(242,160,181,0.07)` | `rgba(176,54,94,0.07)` |
| `-vn-overlay` | Capa sobre imágenes con spoiler | `rgba(18,19,24,0.55)` | `rgba(236,230,223,0.62)` |
| `-vn-danger` | Errores y acciones destructivas (eliminar) | `#FF8F85` | `#B3261E` |
| `-vn-danger-bg` | Fondo de avisos de error | `rgba(255,143,133,0.12)` | `rgba(179,38,30,0.08)` |
| `-vn-backdrop` | Fondo detrás de los diálogos | `#0B0C10` | `#D2CAC2` |
| `-vn-shelf-rosa` … `-vn-shelf-coral` | Los 8 colores de las estanterías: rosa `#F2A0B5`, azul `#8CC8FF`, naranja `#F3B76B`, verde `#9FD8A8`, morado `#C3A6F2`, turquesa `#7FD6CF`, gris `#B4B8C5`, coral `#FF8F85` | iguales | iguales |

Notas:
- El rosa `#F2A0B5` se usa igual en los dos temas como **fondo** del botón principal. Como **texto** sobre fondo claro no se lee bien, por eso existe `-vn-accent-ink` (`#B0365E` en el tema claro).
- Los estados se distinguen también por claridad y forma, no solo por color: azul = completada, naranja = jugando, gris = pendiente, un **aro sin relleno** = en pausa, rojo = abandonada.
- Los fondos de las portadas de ejemplo son marcadores de posición; en la app se usan las imágenes descargadas de VNDB.

## Tipografía

- **Títulos, nombres y navegación:** Zen Kaku Gothic New (Bold 700). Incluye japonés, así que sirve también para los títulos originales.
- **Resto del texto:** Inter (400, 500 y 600).
- Las dos son de Google Fonts con licencia OFL: se pueden incluir en `src/main/resources/fonts/` y cargar con `@font-face` en el CSS o con `Font.loadFont`. Hay que incluir también su licencia (OFL.txt).

Tamaños: título de pantalla 32 px (ficha: 40 px), títulos de sección 24 px, navegación 17 px, texto normal 14 px, metadatos 12–13 px, etiquetas en mayúsculas 11 px con espaciado.

## Medidas

- Radios: tarjetas y paneles 12–14 px, botones 8–10 px, portadas 10 px, etiquetas de estado totalmente redondeadas.
- Cualquier elemento clicable mide al menos 44 px de alto.
- Contenido principal: 32 px arriba, 40 px a los lados. Separación entre bloques: 24–36 px.
- Portadas en proporción 2:3; imágenes de personaje 3:4.

## Barra lateral

De arriba abajo:
1. Logo (icono de los dos libros + «VN Launcher»).
2. Navegación: `Biblioteca`, `Buscar`, `Ajustes`, sin números y con el texto alineado a la izquierda, a la misma altura que «ESTADO» y el logo (26 px). La sección activa lleva el texto en color normal y un **marcapáginas** rosa pegado al borde izquierdo (8 × 40 px, con muesca en V abajo). Las inactivas, en `-vn-muted`.
3. **Estado** (`biblioteca-1-0-*.png`): Jugando, Pendientes, Completadas, En pausa y Abandonadas, con un punto del color del estado (un aro en En pausa) y el número de VNs. Al pulsar, abren la biblioteca filtrada (se combina con el filtro de etiqueta); el que se está filtrando sale resaltado.
4. **Mis estanterías:** «MIS ESTANTERÍAS» con un «+» a la derecha; cada estantería con un cuadradito de su color (8 × 8, radio 2) y su contador; al final, «+ Nueva estantería» en `-vn-accent-ink`. Se reordenan arrastrando (una línea rosa marca dónde caerá) y con clic derecho → «Editar…». Estado y estanterías van en una zona con scroll, para que muchas estanterías no empujen el contador de VNDB.
5. Abajo del todo: contador de peticiones a VNDB («14/200», barra fina). El tema se cambia en Ajustes → Apariencia.
6. De fondo, «ビジュアルノベル» en vertical, 58 px, color `-vn-watermark`. Puramente decorativo.

«Recientes» desapareció en la 1.0: la última VN jugada ya está en «Continuar».

## Ajustes, diálogos y estados

- **Ajustes:** cada bloque es una tarjeta con filas de «etiqueta + explicación» a la izquierda y el control a la derecha. Los grupos de opciones (tema, difuminar imágenes, estado de ruta) son botones segmentados: un `ToggleGroup` de `ToggleButton` con estilo propio. Los interruptores de sí/no son un `ToggleButton` con forma de pastilla.
- **Tema «Según Windows»:** JavaFX 22 o superior lo da con `Platform.getPreferences().getColorScheme()`, y se puede escuchar para cambiar al momento.
- **Difuminar imágenes:** «No difuminar», «Solo explícitas» y «Sugerentes y explícitas» corresponden a la valoración `image.sexual` de VNDB (1 = sugerente, 2 = explícita; es una media, así que se compara con un umbral).
- **Diálogos:** ventana modal (`Stage` con `initModality(Modality.WINDOW_MODAL)` o un `Dialog`) con esquinas de 16 px, sombra y `-vn-backdrop` detrás. Los botones van abajo a la derecha, con la acción principal en rosa a la derecha del todo. «Eliminar ruta» va abajo a la izquierda en `-vn-danger`.
- **Diálogo de ruta:** la heroína se elige con tarjetas de radio (avatar + nombre) entre los personajes «primary», más «Sin heroína» para rutas comunes o True End.
- **Estados vacíos:** siempre explican qué ha pasado y qué hacer, con un botón para hacerlo.
- **Errores:** los de red son avisos dentro de la pantalla (naranja si es un problema, azul si solo hay que esperar); los que bloquean una acción, como lanzar el juego, son diálogos con icono en `-vn-danger`.

## Logo

Archivos en `docs/diseno/logo/`:
- `vn-launcher-app.png` (512 px): icono de la ventana → `stage.getIcons().add(...)`.
- `vn-launcher.ico` (de 16 a 256 px): icono del instalador y del acceso directo → opción `--icon` de `jpackage`.
- `vn-launcher-logo-oscuro.svg` / `-claro.svg`: logo completo para el README.
- Dentro de la interfaz, el icono se dibuja como vector con `SVGPath`. Son cuatro formas (libro delantero, lomo, libro trasero y marcapáginas) definidas en `vn-launcher-icono.svg`, en una caja de 64 × 64. Las tres primeras van con trazo (`stroke`) y el marcapáginas, relleno, todo en `-vn-accent-ink`.

## Detalles de comportamiento que se ven en el diseño

- **Biblioteca:** encima de la cuadrícula, los filtros activos como chips oscuros con ✕ («Estado: Jugando ✕» o «Estantería: Favoritas ✕», y «Etiqueta: Drama ✕») y a la derecha «Ordenar por». Los contadores de cada estado están en la barra lateral. La franja «Continuar» muestra la última VN jugada y su ruta en curso.
- **Ficha simplificada** (`ficha-simplificada-*.png`): título, título original y «Desarrollador · año» (sin enlaces); fila de chips de 34 px sin rótulos: el estado como pastilla con su punto, «· automático» o «· fijado» y una flecha; las estanterías (cuadradito de color, nombre y ×, fondo `-vn-surface2`) y un «+» redondo de 34 px con borde discontinuo («Añadir a estantería»), que abre la lista de todas con casilla y «+ Nueva estantería…». Debajo, «Jugar» y dos botones cuadrados de 52 px: engranaje («Opciones del juego») y «⋯» («Más opciones»: abrir el programa de configuración si hay, Ver en VNDB, Actualizar desde VNDB y, tras un separador, Quitar de la biblioteca en `-vn-danger`). A la derecha del «⋯», en `-vn-muted` y 14 px, lo que pasa al actualizar («✓ Actualizado» en `-vn-blue`, 3 s). Los datos en una línea de texto con los valores en negrita, los «·» en `-vn-faint` y una barra de 80 × 5 px para las rutas; las etiquetas que quepan en una línea (máximo 5) y «+N más» como enlace; la descripción a 3 líneas con «Leer más».
- **Opciones del juego** (`opciones-juego-*.png`): título con el nombre de la VN debajo y ×. «Ejecutable»: la ruta en un campo de solo lectura (fondo `-vn-bg`, letra monoespaciada, recortada por el principio) y «Cambiar…». «Programa de configuración»: caja de borde discontinuo «Sin configurar» con «Elegir…» en rosa, o la ruta con «Cambiar…» y «Quitar»; ayuda debajo. «Lanzar con Locale Emulator» con su ayuda y un interruptor. «Listo» abajo a la derecha. Los cambios se guardan al momento.
- **Diálogo de estantería** (`estanteria-dialogo-*.png`): «Nombre» (máximo 40 caracteres, sin repetir), «Color» (8 muestras de 36 px; la elegida con un aro del color del texto) y «Así se verá en la barra lateral». Al editar: «Posición 2 de 3» con «Subir» y «Bajar», y «Eliminar estantería» abajo a la izquierda (pide confirmación; las novelas no se borran).
- **Estado de una VN** (`ficha-estado-*.png`): la pastilla de estado de la ficha abre una lista que ofrece «Automático — Según tus rutas y partidas (ahora: Jugando)» y los cinco estados; En pausa y Abandonada llevan «Fijado a mano» debajo (nunca salen solos) y el elegido, fondo `-vn-sel` y ✓ en `-vn-accent-ink`. Lo fijado a mano se queda aunque se juegue, hasta volver a «Automático».
- **Ficha:** las rutas se ordenan arrastrando (asa de seis puntos a la izquierda). Los personajes con spoiler salen difuminados con un botón «Mostrar». Los que no tienen ruta llevan un botón «Marcar con ruta».
- **Búsqueda:** se busca al pulsar Enter. «N resultados» va encima de la lista y del panel, y el panel empieza a la altura de la primera tarjeta. A la derecha, un panel fijo de 540 px (no hace scroll con la lista): portada de 180 × 270 con título, título original, «Desarrollador · año» y el estado de desarrollo; seis datos en dos columnas; 5 etiquetas sin spoilers; 3 capturas en 16:9; descripción a 6 líneas con «Leer más»; y abajo «Añadir a la biblioteca» o «✓ En tu biblioteca» + «Abrir ficha».

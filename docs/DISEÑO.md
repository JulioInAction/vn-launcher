# Diseño de la interfaz — VN Launcher

Guía visual para la interfaz JavaFX. Las capturas de `docs/diseno/capturas/` son la referencia de cómo debe quedar cada pantalla; este documento da los valores exactos.

## Pantallas

| Captura | Pantalla | Fase |
|---|---|---|
| `biblioteca-oscuro.png` / `biblioteca-claro.png` | Biblioteca: franja «Continuar», filtros por estado, cuadrícula de portadas | 2 |
| `ficha-oscuro.png` / `ficha-claro.png` | Ficha de la VN: datos, Jugar, rutas en orden, personajes «primary» | 2, 3 y 4 |
| `ficha-capturas-derecha-oscuro.png` / `-claro.png` | Ficha con etiquetas (chips, «+N más») y capturas a la derecha | Ficha: etiquetas y galería de capturas (hecho) |
| `ficha-capturas-estrecha-oscuro.png` | Ficha con la ventana estrecha: la galería baja debajo de la cabecera | Hecho |
| `visor-capturas.png` | Visor de capturas a pantalla completa | Pendiente |
| `buscar-oscuro.png` / `buscar-claro.png` | Búsqueda en VNDB con vista previa del resultado | 1 (rehacer al aplicar el diseño) |
| `ajustes.png` | Ajustes: Locale Emulator, tema, contenido, datos, cuenta de VNDB, acerca de | 5 |
| `dialogo-ruta.png` | Diálogo para crear o editar una ruta | 4 |
| `vacio-biblioteca.png`, `vacio-busqueda.png`, `vacio-personajes.png` | Estados vacíos | 2, 1 y 3 |
| `cargando-portadas.png` | Carga de portadas con marcadores de posición | 2 |
| `error-sin-conexion.png`, `error-limite.png` | Avisos de la búsqueda: sin conexión y límite de 200 peticiones | 1 |
| `error-ejecutable.png`, `error-locale-emulator.png` | Diálogos al lanzar: falta el .exe o falta LEProc.exe | 5 |

## Temas y colores

Hay dos temas, **oscuro** (por defecto) y **claro**. El usuario lo cambia con el botón de la parte de abajo de la barra lateral y se guarda en la tabla `setting` (clave `theme`).

En JavaFX los colores se definen una sola vez como *looked-up colors* en `.root`, y el tema claro los sobrescribe con una clase de estilo:

```css
.root { -vn-bg: #121318; /* ... */ }
.root.tema-claro { -vn-bg: #F7F4F1; /* ... */ }
.tarjeta { -fx-background-color: -vn-surface; }
```

Cambiar de tema = añadir o quitar `tema-claro` de `scene.getRoot().getStyleClass()`. Ningún control lleva colores escritos a mano: todos usan estas variables.

| Variable | Uso | Oscuro | Claro |
|---|---|---|---|
| `-vn-bg` | Fondo de la ventana | `#121318` | `#F7F4F1` |
| `-vn-side` | Barra lateral | `#16171D` | `#FFFFFF` |
| `-vn-surface` | Tarjetas y paneles | `#1F2129` | `#FFFFFF` |
| `-vn-surface2` | Fondo secundario (huecos de imagen) | `#282B35` | `#F0EBE7` |
| `-vn-sel` | Fila seleccionada | `#262833` | `#FDF1F4` |
| `-vn-border` | Bordes suaves | `#2A2D37` | `#E6E0DB` |
| `-vn-border2` | Bordes de botones e inputs | `#3A3D49` | `#D3CBC5` |
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
| `-vn-grey-bg` | Fondo de la etiqueta gris | `rgba(164,168,182,0.14)` | `rgba(94,91,104,0.10)` |
| `-vn-chip-on-bg` | Filtro activo (fondo) | `#ECEDF2` | `#1D1B22` |
| `-vn-chip-on-text` | Filtro activo (texto) | `#121318` | `#FFFFFF` |
| `-vn-track` | Fondo de barras de progreso | `#30333E` | `#E6E0DB` |
| `-vn-watermark` | Texto japonés de fondo en la barra lateral | `rgba(242,160,181,0.07)` | `rgba(176,54,94,0.07)` |
| `-vn-overlay` | Capa sobre imágenes con spoiler | `rgba(18,19,24,0.55)` | `rgba(247,244,241,0.62)` |
| `-vn-danger` | Errores y acciones destructivas (eliminar) | `#FF8F85` | `#B3261E` |
| `-vn-danger-bg` | Fondo de avisos de error | `rgba(255,143,133,0.12)` | `rgba(179,38,30,0.08)` |
| `-vn-backdrop` | Fondo detrás de los diálogos | `#0B0C10` | `#DCD5D0` |

Notas:
- El rosa `#F2A0B5` se usa igual en los dos temas como **fondo** del botón principal. Como **texto** sobre fondo claro no se lee bien, por eso existe `-vn-accent-ink` (`#B0365E` en el tema claro).
- Los estados se distinguen también por claridad, no solo por color: azul = completada, naranja = jugando, gris = pendiente.
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
2. Navegación numerada: `01 Biblioteca`, `02 Buscar en VNDB`, `03 Ajustes`. La sección activa lleva el texto en color normal y un **marcapáginas** rosa pegado al borde izquierdo (8 × 40 px, con muesca en V abajo). Las inactivas, en `-vn-muted`.
3. **Estanterías:** Jugando, Pendientes y Completadas, con un punto del color del estado y el número de VNs. Al pulsar, abren la biblioteca filtrada.
4. **Recientes:** portadas pequeñas (48 × 72) de las 3 últimas VNs jugadas.
5. Abajo del todo: contador de peticiones a VNDB («14/200», barra fina) y botón para cambiar de tema.
6. De fondo, «ビジュアルノベル» en vertical, 58 px, color `-vn-watermark`. Puramente decorativo.

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

- **Biblioteca:** los filtros (Todas, Jugando, Pendientes, Completadas) muestran el número de VNs de cada estado. La franja «Continuar» muestra la última VN jugada y su ruta en curso.
- **Ficha:** las rutas se ordenan arrastrando (asa de seis puntos a la izquierda). Los personajes con spoiler salen difuminados con un botón «Mostrar». Los que no tienen ruta llevan un botón «Marcar con ruta».
- **Búsqueda:** se busca al pulsar Enter. «N resultados» va encima de la lista y del panel, y el panel empieza a la altura de la primera tarjeta. A la derecha, un panel fijo de 540 px (no hace scroll con la lista): portada de 180 × 270 con título, título original, «Desarrollador · año» y el estado de desarrollo; seis datos en dos columnas; 5 etiquetas sin spoilers; 3 capturas en 16:9; descripción a 6 líneas con «Leer más»; y abajo «Añadir a la biblioteca» o «✓ En tu biblioteca» + «Abrir ficha».

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
- [ ] Fase 6: tiempo de juego
- [ ] Fase 7: pulido

## Pendiente para la Fase 6 (del diseño)

- Tarjeta "Tiempo jugado" en la ficha.
- Biblioteca: franja "Continuar" con la última VN jugada y su ruta en curso, filtros por estado
  con contadores (Todas / Jugando / Pendientes / Completadas), orden ("Último jugado"...) y
  horas jugadas en cada portada.

## Pendiente para la Fase 7

- Pantalla de **Ajustes** (entrada "03 Ajustes" de la barra lateral) con, entre otros:
  - desactivar el difuminado ("censura") de imágenes sensibles;
  - cambiar la ruta de Locale Emulator (`LEProc.exe`), que ahora se pide la primera vez que hace falta;
  - ver la carpeta de datos.
- Botón "Actualizar desde VNDB" en la ficha.
- Ejecutable secundario por VN (p. ej. `config.exe`).

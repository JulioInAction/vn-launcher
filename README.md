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
- [x] Fase 1: cliente VNDB y búsqueda
- [x] Fase 2: biblioteca local
- [x] Fase 3: personajes
- [x] Fase 4: rutas
- [x] Fase 5: lanzamiento (+ ficha de VN rediseñada según el prototipo)
- [ ] Fase 6: tiempo de juego
- [ ] Fase 7: pulido

## Pendiente para la Fase 6 (del prototipo)

- Tarjeta "Tiempo jugado" en la ficha.
- Biblioteca: banner "Continuar" con la última VN jugada, filtros por estado
  (Todas / Jugando / Pendientes / Completadas), orden ("Último jugado"...) y en cada portada
  su estado y horas jugadas.

## Pendiente para la Fase 7

- Menú de **Ajustes** (entrada "Ajustes" del menú lateral) con, entre otros:
  - desactivar el difuminado ("censura") de imágenes sensibles;
  - cambiar la ruta de Locale Emulator (`LEProc.exe`), que ahora se pide la primera vez que hace falta;
  - ver la carpeta de datos.
- Búsqueda rediseñada según el prototipo (miniaturas, botón "Añadir" en cada resultado, panel lateral).
- Botón "Actualizar desde VNDB" en la ficha.
- Ejecutable secundario por VN (p. ej. `config.exe`).

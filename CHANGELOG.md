# Cambios

Todos los cambios importantes de VN Launcher. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones, [SemVer](https://semver.org/lang/es/).

## [1.0.0] - 2026-11-22

Primera versión estable.

### Novedades
- **Vista previa de la ficha:** «Ver ficha» (o doble clic) en Buscar abre la ficha de un resultado que aún
  no está en la biblioteca, con su duración, valoración e idiomas. «Añadir a la biblioteca» la convierte en
  la ficha normal sin salir de la pantalla, y «‹ Buscar» vuelve a la búsqueda tal como estaba.
- **Buscador en la biblioteca** (título, título original y desarrollador; sin distinguir mayúsculas, tildes
  ni ancho completo; Ctrl+F y Esc) y botón del ojo para ver las imágenes sensibles durante la sesión.
- **«Ordenar por» recordado**, también al exportar e importar el perfil.
- **Estado manual** (Jugando, Pendiente, Completada, En pausa, Abandonada) y **estanterías** propias con
  color, para organizar la biblioteca.
- **Menú de clic derecho** en las portadas: Jugar, abrir el programa de configuración, abrir la carpeta,
  estantería, estado y quitar.
- **Ejecutables perdidos:** la portada lo indica, la ficha avisa y deja buscar el `.exe` de nuevo.
- **Copias de seguridad automáticas** (antes de actualizar, semanales y antes de restaurar o importar; se
  guardan las 5 últimas) y **restaurar una copia** reiniciando la aplicación.
- **Exportar e importar el perfil** en un único `.zip` (biblioteca, rutas, tiempo de juego, estanterías,
  ajustes y portadas propias).
- **Registro de errores** y diálogo «Algo ha fallado» con los detalles técnicos para adjuntar al informar.
- **Aviso de actualizaciones** en Ajustes → Acerca de (con un punto junto a «Ajustes» cuando hay una versión
  nueva), que se puede desactivar.
- Una sola ventana de VN Launcher a la vez.
- Los diálogos y el visor de capturas se cierran también con un clic fuera.

### Cambios
- Quitar una novela de la biblioteca borra también su portada propia.
- Las novelas que solo se miraron en la búsqueda se limpian de la caché a los 30 días.
- «Abrir carpeta» abre el Explorador de archivos (antes abría el navegador en Windows).

### Desde la 0.1.0
La 0.1.0 ya incluía la búsqueda en VNDB, la biblioteca, los personajes, las rutas, el lanzamiento (con Locale
Emulator opcional), el tiempo de juego, los temas claro y oscuro y el instalador para Windows. La
instalación de la 1.0.0 sustituye a la de la 0.1.0 y conserva tus datos.

[1.0.0]: https://github.com/JulioInAction/vn-launcher/releases/tag/v1.0.0

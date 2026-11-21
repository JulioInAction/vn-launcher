/**
 * Descriptor del módulo. Declara de qué módulos dependemos ("requires")
 * y qué paquetes dejamos ver a otros módulos ("exports"/"opens").
 */
module com.vnlauncher {
    requires javafx.controls;
    requires javafx.fxml;

    // java.util.logging: el registro de errores en logs/ (LogSetup). Los System.Logger de la app
    // escriben en él automáticamente cuando este módulo está presente.
    requires java.logging;

    // java.sql = API de JDBC (Connection, PreparedStatement...).
    requires java.sql;
    // El driver de SQLite no tiene module-info propio; Java lo trata como
    // "módulo automático" con este nombre. Lo necesitamos para que
    // DriverManager lo encuentre en el module path.
    requires org.xerial.sqlitejdbc;

    // Cliente HTTP del JDK (java.net.http.HttpClient) y Jackson para el JSON.
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    // JavaFX crea la clase Application por reflexión: necesita ver el paquete.
    exports com.vnlauncher.app to javafx.graphics;

    // FXMLLoader instancia los controladores y rellena los campos @FXML
    // (aunque sean privados) por reflexión. "opens" permite esa reflexión.
    opens com.vnlauncher.ui to javafx.fxml;
    // styles.css pide un skin propio (-fx-skin: SharpScrollPaneSkin) y javafx.controls lo crea por
    // reflexión: necesita ver el paquete.
    exports com.vnlauncher.ui to javafx.controls;

    // Jackson también usa reflexión para leer/crear los records de los DTO y el manifest.json de
    // los perfiles exportados (model.ProfileManifest).
    opens com.vnlauncher.api.dto to com.fasterxml.jackson.databind;
    opens com.vnlauncher.model to com.fasterxml.jackson.databind;
}

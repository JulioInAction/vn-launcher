/**
 * Descriptor del módulo. Declara de qué módulos dependemos ("requires")
 * y qué paquetes dejamos ver a otros módulos ("exports"/"opens").
 */
module com.vnlauncher {
    requires javafx.controls;
    requires javafx.fxml;

    // java.sql = API de JDBC (Connection, PreparedStatement...).
    requires java.sql;
    // El driver de SQLite no tiene module-info propio; Java lo trata como
    // "módulo automático" con este nombre. Lo necesitamos para que
    // DriverManager lo encuentre en el module path.
    requires org.xerial.sqlitejdbc;

    // JavaFX crea la clase Application por reflexión: necesita ver el paquete.
    exports com.vnlauncher.app to javafx.graphics;

    // FXMLLoader instancia los controladores y rellena los campos @FXML
    // (aunque sean privados) por reflexión. "opens" permite esa reflexión.
    opens com.vnlauncher.ui to javafx.fxml;
}

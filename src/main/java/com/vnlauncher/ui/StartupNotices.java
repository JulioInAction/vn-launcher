package com.vnlauncher.ui;

import com.vnlauncher.app.AppInfo;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.service.PendingOperation;
import com.vnlauncher.service.PendingOperations;
import com.vnlauncher.util.Formats;
import javafx.application.Platform;
import javafx.scene.Node;

/** Avisos al arrancar que no son errores del arranque en sí. */
public final class StartupNotices {

    static final String ALREADY_RUNNING_TITLE = "VN Launcher ya está abierto";
    static final String ALREADY_RUNNING = "Búscalo en la barra de tareas. Solo puede haber una ventana "
            + "abierta a la vez, para que los cambios de una no pisen los de la otra.";

    static final String RESTORED_TITLE = "Copia restaurada";
    /** «… como estaba el 21/11/2026, 10:42. Si quieres deshacerlo, restaura la copia «Antes de restaurar una copia».» */
    static final String RESTORED = "Tu biblioteca vuelve a estar como estaba el %s. Si quieres deshacerlo, "
            + "restaura la copia «%s».";
    static final String RESTORE_FAILED = "No se ha podido restaurar la copia.";

    static final String IMPORTED_TITLE = "Perfil importado";
    /** «… el perfil exportado el 21/11/2026, 10:42. Si quieres deshacerlo, restaura la copia «Antes de importar un perfil».» */
    static final String IMPORTED = "Ya tienes aquí el perfil exportado el %s. Si quieres deshacerlo, "
            + "restaura la copia «%s» desde Ajustes.";
    static final String IMPORT_FAILED = "No se ha podido importar el perfil.";

    /** Si ni siquiera se pudo leer qué había que hacer. */
    static final String PENDING_FAILED = "No se ha podido terminar de restaurar la copia o importar el perfil.";
    static final String FAILED_ADVICE = "Tu biblioteca sigue como estaba. Si vuelve a pasar, "
            + "adjunta el registro al informar del problema.";

    private StartupNotices() {
    }

    /** Ya hay otra VN Launcher abierta. Sin ventana propia: el aviso sale centrado en la pantalla. */
    public static void alreadyRunning() {
        Dialogs.info(null, ALREADY_RUNNING_TITLE, ALREADY_RUNNING);
    }

    /**
     * Cómo fue lo que quedó pendiente al reiniciar (restaurar una copia o importar un perfil). Se
     * enseña cuando la ventana ya está abierta y pintada.
     */
    public static void afterPendingOperation(Node owner, PendingOperations.Outcome outcome) {
        Platform.runLater(() -> {
            switch (outcome) {
                case PendingOperations.Outcome.Done done -> showDone(owner, done.operation());
                // Ya está en el registro (lo escribió PendingOperations): solo se avisa
                case PendingOperations.Outcome.Failed failed ->
                        ErrorReporter.notifyUser(failedMessage(failed.operation()), FAILED_ADVICE, failed.error());
            }
        });
    }

    private static void showDone(Node owner, PendingOperation operation) {
        String date = Formats.DATE_TIME.format(operation.dataDate());
        switch (operation.kind()) {
            case RESTORE -> Dialogs.info(owner, RESTORED_TITLE, String.format(RESTORED, date,
                    BackupKind.BEFORE_RESTORE.describe(AppInfo.VERSION)));
            case IMPORT -> Dialogs.info(owner, IMPORTED_TITLE, String.format(IMPORTED, date,
                    BackupKind.BEFORE_IMPORT.describe(AppInfo.VERSION)));
        }
    }

    static String failedMessage(PendingOperation operation) {
        if (operation == null) {
            return PENDING_FAILED;
        }
        return switch (operation.kind()) {
            case RESTORE -> RESTORE_FAILED;
            case IMPORT -> IMPORT_FAILED;
        };
    }
}

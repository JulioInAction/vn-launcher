package com.vnlauncher.ui;

import com.vnlauncher.app.AppInfo;
import com.vnlauncher.model.BackupKind;
import com.vnlauncher.service.RestoreService;
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
    static final String RESTORE_FAILED_ADVICE = "Tu biblioteca sigue como estaba. Si vuelve a pasar, "
            + "adjunta el registro al informar del problema.";

    private StartupNotices() {
    }

    /** Ya hay otra VN Launcher abierta. Sin ventana propia: el aviso sale centrado en la pantalla. */
    public static void alreadyRunning() {
        Dialogs.info(null, ALREADY_RUNNING_TITLE, ALREADY_RUNNING);
    }

    /**
     * Cómo fue lo que quedó pendiente al reiniciar (restaurar una copia). Se enseña cuando la
     * ventana ya está abierta y pintada.
     */
    public static void afterPendingOperation(Node owner, RestoreService.Outcome outcome) {
        Platform.runLater(() -> {
            switch (outcome) {
                case RestoreService.Outcome.Done done -> Dialogs.info(owner, RESTORED_TITLE,
                        String.format(RESTORED, Formats.DATE_TIME.format(done.operation().dataDate()),
                                BackupKind.BEFORE_RESTORE.describe(AppInfo.VERSION)));
                // Ya está en el registro (lo escribió RestoreService): solo se avisa
                case RestoreService.Outcome.Failed failed ->
                        ErrorReporter.notifyUser(RESTORE_FAILED, RESTORE_FAILED_ADVICE, failed.error());
            }
        });
    }
}

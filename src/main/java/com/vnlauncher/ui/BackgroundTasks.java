package com.vnlauncher.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Atajo para el patrón "trabajo en segundo plano → resultado en la interfaz" que se repite
 * en todos los controladores (ver la explicación larga en SearchController).
 *
 * <p><b>Errores.</b> Un Task guarda la excepción de su trabajo y la entrega en setOnFailed: NUNCA
 * llega al manejador global de errores. Por eso aquí se registra siempre (aunque la pantalla la
 * gestione con su propio aviso), y la variante con mensaje la manda a {@link ErrorReporter}.
 */
final class BackgroundTasks {

    private static final System.Logger LOG = System.getLogger(BackgroundTasks.class.getName());

    private BackgroundTasks() {
    }

    /**
     * @param work      se ejecuta en segundo plano; NO debe tocar la interfaz
     * @param onSuccess se ejecuta en el hilo de la interfaz con el resultado
     * @param onError   se ejecuta en el hilo de la interfaz si work lanzó una excepción
     * @return la tarea; task.cancel() interrumpe el hilo (y entonces no se llama a ninguno de los dos)
     */
    static <T> Task<T> run(ExecutorService executor, Callable<T> work,
                           Consumer<T> onSuccess, Consumer<Throwable> onError) {
        return start(executor, work, onSuccess, onError, true);
    }

    /**
     * Igual, pero si falla se muestra el diálogo «Algo ha fallado» con {@code errorMessage}.
     * Para trabajo que no debería fallar nunca (guardar en la base de datos...).
     *
     * @param errorMessage frase completa: «No se ha podido guardar la ruta.»
     */
    static <T> Task<T> run(ExecutorService executor, Callable<T> work, Consumer<T> onSuccess,
                           String errorMessage) {
        // ErrorReporter ya lo escribe en el registro: aquí no se registra otra vez
        return start(executor, work, onSuccess, error -> ErrorReporter.report(errorMessage, error), false);
    }

    private static <T> Task<T> start(ExecutorService executor, Callable<T> work, Consumer<T> onSuccess,
                                     Consumer<Throwable> onError, boolean logFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (logFailure) {
                // Los esperados (sin conexión...) sin traza: son normales y la pantalla ya avisa
                if (ErrorReporter.isExpected(error)) {
                    LOG.log(System.Logger.Level.WARNING, "Tarea en segundo plano fallida: {0}", String.valueOf(error));
                } else {
                    LOG.log(System.Logger.Level.WARNING, "Tarea en segundo plano fallida", error);
                }
            }
            onError.accept(error);
        });
        executor.submit(task);
        return task; // por si quien llama quiere cancelarla (task.cancel())
    }
}

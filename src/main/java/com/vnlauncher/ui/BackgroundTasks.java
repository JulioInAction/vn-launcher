package com.vnlauncher.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Atajo para el patrón "trabajo en segundo plano → resultado en la interfaz" que se repite
 * en todos los controladores (ver la explicación larga en SearchController).
 */
final class BackgroundTasks {

    private BackgroundTasks() {
    }

    /**
     * @param work      se ejecuta en segundo plano; NO debe tocar la interfaz
     * @param onSuccess se ejecuta en el hilo de la interfaz con el resultado
     * @param onError   se ejecuta en el hilo de la interfaz si work lanzó una excepción
     */
    static <T> void run(ExecutorService executor, Callable<T> work,
                        Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));
        executor.submit(task);
    }
}

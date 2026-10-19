package com.practica2.paralelismo;

import com.practica2.algoritmo.FiltroSobel;
import java.awt.image.BufferedImage;

/**
 * ============================================================================
 * CONCEPTO TEÓRICO: UNIDAD DE EJECUCIÓN PARALELA (THREAD DE JAVA)
 * ============================================================================
 * Cada instancia de HiloSobel hereda de java.lang.Thread y representa una tarea
 * paralela asignada a un hilo nativo del Sistema Operativo gestionado por la JVM.
 *
 * MAPEO DE TAREAS A PROCESADORES (TASK-TO-PROCESSOR MAPPING):
 * - El subdominio de filas continuas [filaInicio, filaFin) se procesa en este hilo.
 * - Todos los hilos acceden concurrentemente a la misma memoria RAM (Heap compartido)
 *   donde residen las instancias de 'origen' y 'destino'.
 * ============================================================================
 */
public class HiloSobel extends Thread {

    private final BufferedImage origen;
    private final BufferedImage destino;
    private final int filaInicio;
    private final int filaFin;

    /**
     * Constructor del hilo de procesamiento Sobel.
     *
     * @param origen     Referencia compartida a la imagen de entrada.
     * @param destino    Referencia compartida a la imagen de salida.
     * @param filaInicio Índice de fila inicial (inclusiva).
     * @param filaFin    Índice de fila final (exclusiva).
     */
    public HiloSobel(BufferedImage origen, BufferedImage destino, int filaInicio, int filaFin) {
        this.origen = origen;
        this.destino = destino;
        this.filaInicio = filaInicio;
        this.filaFin = filaFin;
    }

    /**
     * Método ejecutado concurrentemente al llamar a start().
     * Invoca el filtro Sobel para su porción de imagen asignada.
     */
    @Override
    public void run() {
        FiltroSobel.aplicarSobel(origen, destino, filaInicio, filaFin);
    }

    public int getFilaInicio() {
        return filaInicio;
    }

    public int getFilaFin() {
        return filaFin;
    }
}

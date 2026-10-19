package com.practica2.paralelismo;

import com.practica2.algoritmo.FiltroSobel;
import com.practica2.utils.RegistradorTiempos;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * ============================================================================
 * CONCEPTO TEÓRICO: DISEÑO DE ALGORITMOS PARALELOS (METODOLOGÍA DE FOSTER)
 * ============================================================================
 * Esta clase orquesta la ejecución paralela aplicando las 4 etapas
 * fundamentales
 * del cómputo de alto rendimiento:
 *
 * 1. PARTICIONAMIENTO / DESCOMPOSICIÓN DE DOMINIO:
 * Se descompone la matriz de la imagen 2D a lo largo de su dimensión vertical
 * (descomposición 1D por franjas horizontales de filas).
 *
 * 2. COMUNICACIÓN:
 * En el modelo de memoria compartida, no se requiere paso de mensajes explícito
 * (MPI). Los hilos leen concurrentemente las zonas limítrofes (halo cells)
 * directamente de la memoria RAM común.
 *
 * 3. AGLOMERACIÓN:
 * Se agrupan filas contiguas en un solo bloque continuo por hilo. Esto mejora
 * drásticamente la localidad espacial de la caché de la CPU (L1/L2/L3),
 * minimizando fallos de línea de caché (Cache Misses).
 *
 * 4. MAPEO Y BALANCE DE CARGA (LOAD BALANCING):
 * Se calcula el número base de filas por hilo (alto / numHilos) y el residuo
 * (alto % numHilos). El residuo se distribuye equitativamente (+1 fila a los
 * primeros hilos) logrando un balance de carga óptimo sin hilos ociosos.
 * ============================================================================
 */
public class GestorParalelo {

    /**
     * Procesa la imagen secuencialmente en el hilo actual sin sobrecostos de
     * creación de hilos (Baseline Ts).
     *
     * @param origen  Imagen de entrada.
     * @param destino Imagen de salida.
     * @return Tiempo de procesamiento secuencial medido en milisegundos (Ts).
     */
    public static double procesarSecuencial(BufferedImage origen, BufferedImage destino) {
        long tiempoInicioNano = System.nanoTime();
        FiltroSobel.aplicarSobel(origen, destino);
        long tiempoFinNano = System.nanoTime();
        return (tiempoFinNano - tiempoInicioNano) / 1_000_000.0;
    }

    /**
     * Obtiene el conjunto ordenado de configuraciones de hilos a evaluar: {2, 4,
     * núcleos, núcleos * 2}.
     *
     * @return TreeSet con las cantidades de hilos para el banco de pruebas.
     */
    public static TreeSet<Integer> obtenerConfiguracionesHilos() {
        int nucleosLogicos = Runtime.getRuntime().availableProcessors();
        TreeSet<Integer> configuraciones = new TreeSet<>();
        configuraciones.add(2);
        configuraciones.add(4);
        configuraciones.add(nucleosLogicos);
        configuraciones.add(nucleosLogicos * 2);
        return configuraciones;
    }

    /**
     * Ejecuta el banco de pruebas multihilo sobre la imagen para cada configuración
     * de hilos,
     * calculando y registrando métricas de aceleración (Speedup) y eficiencia.
     *
     * @param imagenOrigen         Imagen de entrada.
     * @param nombreImagen         Nombre del archivo de imagen.
     * @param resolucion           Cadena con dimensiones (ej. "3840x2160").
     * @param ts                   Tiempo base secuencial (ms).
     * @param configuracionesHilos Colección de cantidad de hilos a desplegar.
     * @param registrador          Instancia donde se almacenarán las métricas.
     * @return Imagen resultado producida por el último nivel de paralelismo.
     */
    public static BufferedImage ejecutarPruebasParalelas(
            BufferedImage imagenOrigen,
            String nombreImagen,
            String resolucion,
            double ts,
            Set<Integer> configuracionesHilos,
            RegistradorTiempos registrador) {

        System.out.println("  -> Ejecutando pruebas PARALELAS multihilo...");
        int ancho = imagenOrigen.getWidth();
        int alto = imagenOrigen.getHeight();
        BufferedImage imagenDestinoParalelo = null;

        for (int p : configuracionesHilos) {
            if (p <= 1)
                continue;

            imagenDestinoParalelo = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);

            System.out.printf("     Desplegando pool de %2d hilos trabajadores (Threads)... ", p);
            double tp = procesarEnParalelo(imagenOrigen, imagenDestinoParalelo, p);
            double speedup = ts / tp;
            double eficiencia = (speedup / p) * 100.0;
            System.out.printf(Locale.US, "Tp: %7.2f ms | Speedup: %5.2fx | Eficiencia: %6.2f%%%n",
                    tp, speedup, eficiencia);

            registrador.agregarRegistro(nombreImagen, resolucion, p, ts, tp);
        }

        return imagenDestinoParalelo;
    }

    /**
     * Procesa la imagen aplicando el filtro Sobel distribuido entre 'numHilos'
     * hilos.
     * Mide con alta precisión (System.nanoTime) el tiempo que tardan los hilos en
     * ejecutar su trabajo de cómputo paralelo.
     *
     * @param origen   Imagen de entrada (solo lectura en memoria compartida).
     * @param destino  Imagen de salida donde se almacenan los bordes.
     * @param numHilos Número de hilos de cómputo a desplegar (p).
     * @return Tiempo de procesamiento paralelo medido en milisegundos (Tp).
     */
    public static double procesarEnParalelo(BufferedImage origen, BufferedImage destino, int numHilos) {
        if (numHilos <= 0) {
            throw new IllegalArgumentException("El número de hilos debe ser mayor a 0.");
        }

        int alto = origen.getHeight();
        HiloSobel[] hilos = new HiloSobel[numHilos];

        // --- FASE 1 Y 4: DESCOMPOSICIÓN Y BALANCE DE CARGA ---
        int filasBasePorHilo = alto / numHilos;
        int residuo = alto % numHilos; // Filas sobrantes a repartir

        int filaInicioActual = 0;
        for (int i = 0; i < numHilos; i++) {
            // Repartición equitativa del residuo: los primeros 'residuo' hilos reciben 1
            // fila extra
            int filasParaEsteHilo = filasBasePorHilo + (i < residuo ? 1 : 0);
            int filaFinActual = filaInicioActual + filasParaEsteHilo;

            // --- FASE 3 Y 4: AGLOMERACIÓN Y MAPEO ESTÁTICO A THREADS ---
            hilos[i] = new HiloSobel(origen, destino, filaInicioActual, filaFinActual);

            filaInicioActual = filaFinActual;
        }

        // --- MEDICIÓN PRECISA DEL TIEMPO DE PROCESAMIENTO PARALELO (Tp) ---
        // Se mide estrictamente desde el inicio de los hilos hasta que todos finalizan
        // (Fork-Join)
        long tiempoInicioNano = System.nanoTime();

        // Fork: Disparo y ejecución concurrente de todos los hilos
        for (int i = 0; i < numHilos; i++) {
            hilos[i].start();
        }

        // Join: Sincronización de barrera; el hilo principal espera a que todos
        // terminen
        for (int i = 0; i < numHilos; i++) {
            try {
                hilos[i].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Error: El hilo " + i + " fue interrumpido durante la ejecución.");
            }
        }

        long tiempoFinNano = System.nanoTime();

        // Conversión a milisegundos con precisión decimal
        return (tiempoFinNano - tiempoInicioNano) / 1_000_000.0;
    }
}

package com.practica2.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ============================================================================
 * CONCEPTO TEÓRICO: EVALUACIÓN DE RENDIMIENTO EN HPC
 * ============================================================================
 * Métricas fundamentales aplicadas en esta clase:
 *
 * 1. SPEEDUP / ACELERAMIENTO (S):
 * S(p) = Ts / Tp
 * Indica cuántas veces es más rápido el programa al usar 'p' hilos en
 * comparación con la versión puramente secuencial (1 hilo).
 *
 * 2. EFICIENCIA (E):
 * E(p) = S(p) / p
 * Representa el porcentaje de utilización real de los recursos computacionales.
 * - E = 1.0 (100%): Eficiencia ideal lineal perfecta (sin sobrecostos).
 * - E >= 0.5 (50%): Se considera un sistema paralelo altamente ESCALABLE.
 * - E < 0.5: Pérdida por sobrecostos (overhead de creación de hilos, contención
 * de bus).
 *
 * 3. LEY DE AMDAHL:
 * S_Amdahl(p) = 1 / ( (1 - f) + (f / p) )
 * Asume un tamaño de problema constante (Fixed-size Speedup).
 * Establece un techo asintótico: cuando p -> infinito, S_max = 1 / (1 - f).
 *
 * 4. LEY DE GUSTAFSON:
 * S_Gustafson(p) = (1 - f) + p * f
 * Asume que al disponer de más procesadores, el tamaño del problema también
 * crece proporcionalmente (Scaled Speedup), superando el cuello de botella de
 * Amdahl.
 * ============================================================================
 */
public class RegistradorTiempos {

    public static class MetricaFila {
        public final String nombreImagen;
        public final String resolucion;
        public final int hilos;
        public final double tiempoSecuencialMs;
        public final double tiempoParaleloMs;
        public final double speedup;
        public final double eficiencia;
        public final String estado;

        public MetricaFila(String nombreImagen, String resolucion, int hilos, double tiempoSecuencialMs,
                double tiempoParaleloMs) {
            this.nombreImagen = nombreImagen;
            this.resolucion = resolucion;
            this.hilos = hilos;
            this.tiempoSecuencialMs = tiempoSecuencialMs;
            this.tiempoParaleloMs = tiempoParaleloMs;
            this.speedup = tiempoSecuencialMs / tiempoParaleloMs;
            this.eficiencia = this.speedup / hilos;
            this.estado = (this.eficiencia >= 0.5) ? "Escalable" : "Baja eficiencia";
        }
    }

    private final List<MetricaFila> metricas = new ArrayList<>();

    public void agregarRegistro(String nombreImagen, String resolucion, int hilos, double tiempoSecuencialMs,
            double tiempoParaleloMs) {
        metricas.add(new MetricaFila(nombreImagen, resolucion, hilos, tiempoSecuencialMs, tiempoParaleloMs));
    }

    /**
     * Imprime el encabezado institucional y de la práctica en consola.
     */
    public static void imprimirEncabezadoSistema() {
        System.out.println("\n" + "=".repeat(105));
        System.out.println(
                "   PRÁCTICA 2: DETECCIÓN DE BORDES CON FILTRO SOBEL POR LOTES MEDIANTE HILOS (JAVA THREADS)   ");
        System.out.println(
                "   ASIGNATURA: COMPUTACIÓN PARALELA Y DISTRIBUIDA (HPC)                                       ");
        System.out.println("=".repeat(105));
    }

    /**
     * Detecta e imprime las especificaciones de hardware y entorno del sistema
     * actual.
     */
    public static void imprimirInformacionHardware() {
        int nucleosLogicos = Runtime.getRuntime().availableProcessors();
        long memoriaTotalBytes = Runtime.getRuntime().totalMemory();
        long memoriaMaxBytes = Runtime.getRuntime().maxMemory();

        System.out.println("  [HARDWARE] Núcleos lógicos detectados: " + nucleosLogicos);
        System.out.println("  [HARDWARE] Memoria JVM Asignada: " + (memoriaTotalBytes / (1024 * 1024)) + " MB");
        System.out.println("  [HARDWARE] Memoria JVM Máxima:   " + (memoriaMaxBytes / (1024 * 1024)) + " MB");
        System.out.println(
                "  [SISTEMA]  SO: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        System.out.println("  [SISTEMA]  Java Version: " + System.getProperty("java.version"));
        System.out.println("=".repeat(105) + "\n");
    }

    /**
     * Imprime el listado de imágenes detectadas para procesar.
     */
    public static void imprimirColeccionImagenes(List<File> listaImagenes, String carpetaEntrada) {
        System.out.println("[COLECCIÓN] Se encontraron " + listaImagenes.size() + " imagen(es) para procesar en '"
                + carpetaEntrada + "':");
        for (int i = 0; i < listaImagenes.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + listaImagenes.get(i).getName());
        }
        System.out.println("-".repeat(105));
    }

    /**
     * Imprime el encabezado para el procesamiento individual de una imagen.
     */
    public static void imprimirEncabezadoImagen(int actual, int total, String nombreArchivo,
            String extension, String resolucion,
            double ramMb, double tiempoLecturaMs) {
        System.out.println("\n" + "=".repeat(105));
        System.out.printf("  [PROCESANDO %d/%d] Archivo: %s (Formato: %s)%n",
                actual, total, nombreArchivo, extension.toUpperCase());
        System.out.println("=".repeat(105));
        System.out.printf(Locale.US, "  -> Dimensiones: %s px (%.2f MB en RAM). Tiempo I/O Lectura: %.2f ms%n",
                resolucion, ramMb, tiempoLecturaMs);
    }

    /**
     * Imprime una tabla formateada en la consola con las métricas de una imagen en
     * particular.
     */
    public void imprimirTablaConsola(String nombreImagen) {
        System.out.println("\n" + "=".repeat(105));
        System.out.println("          TABLA DE RENDIMIENTO PARALELO (HPC) - IMAGEN: " + nombreImagen);
        System.out.println("=".repeat(105));
        System.out.printf(Locale.US, "| %-8s | %-12s | %-15s | %-15s | %-10s | %-12s | %-16s |%n",
                "Hilos(p)", "Resolución", "T. Sec (ms)", "T. Par (ms)", "Speedup(S)", "Eficiencia", "Estado");
        System.out.println("-".repeat(105));

        for (MetricaFila m : metricas) {
            if (m.nombreImagen.equalsIgnoreCase(nombreImagen)) {
                System.out.printf(Locale.US, "| %-8d | %-12s | %-15.2f | %-15.2f | %-10.2fx | %-11.2f%% | %-16s |%n",
                        m.hilos,
                        m.resolucion,
                        m.tiempoSecuencialMs,
                        m.tiempoParaleloMs,
                        m.speedup,
                        m.eficiencia * 100.0,
                        m.estado);
            }
        }
        System.out.println("=".repeat(105));
    }

    /**
     * Imprime una tabla consolidada global de todas las imágenes procesadas.
     */
    public void imprimirTablaConsolidadaGlobal() {
        System.out.println("\n" + "=".repeat(125));
        System.out.println(
                "                        RESUMEN CONSOLIDADO GLOBAL DE RENDIMIENTO HPC                        ");
        System.out.println("=".repeat(125));
        System.out.printf(Locale.US, "| %-22s | %-12s | %-8s | %-13s | %-13s | %-10s | %-11s | %-15s |%n",
                "Imagen", "Resolución", "Hilos", "T. Sec (ms)", "T. Par (ms)", "Speedup(S)", "Eficiencia", "Estado");
        System.out.println("-".repeat(125));

        for (MetricaFila m : metricas) {
            System.out.printf(Locale.US,
                    "| %-22s | %-12s | %-8d | %-13.2f | %-13.2f | %-10.2fx | %-10.2f%% | %-15s |%n",
                    truncarTexto(m.nombreImagen, 22),
                    m.resolucion,
                    m.hilos,
                    m.tiempoSecuencialMs,
                    m.tiempoParaleloMs,
                    m.speedup,
                    m.eficiencia * 100.0,
                    m.estado);
        }
        System.out.println("=".repeat(125));
    }

    /**
     * Exporta todas las métricas de todas las imágenes a un archivo CSV delimitado
     * por punto y coma (;).
     */
    public void exportarCSV(String rutaArchivo) throws IOException {
        File archivo = new File(rutaArchivo);
        File directorio = archivo.getParentFile();
        if (directorio != null && !directorio.exists()) {
            directorio.mkdirs();
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo, StandardCharsets.UTF_8))) {
            bw.write(
                    "Imagen;Resolucion;Hilos;Tiempo_Secuencial_ms;Tiempo_Paralelo_ms;Speedup;Eficiencia_Pct;Estado_Escalabilidad");
            bw.newLine();

            for (MetricaFila m : metricas) {
                String linea = String.format(Locale.US, "%s;%s;%d;%.2f;%.2f;%.2f;%.2f;%s",
                        m.nombreImagen,
                        m.resolucion,
                        m.hilos,
                        m.tiempoSecuencialMs,
                        m.tiempoParaleloMs,
                        m.speedup,
                        m.eficiencia * 100.0,
                        m.estado);
                bw.write(linea);
                bw.newLine();
            }
        }
        System.out.println("  [CSV] Métricas exportadas exitosamente a: " + archivo.getAbsolutePath());
    }

    private static String truncarTexto(String texto, int longitudMax) {
        if (texto.length() <= longitudMax) {
            return texto;
        }
        return texto.substring(0, longitudMax - 3) + "...";
    }
}

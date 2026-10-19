package com.practica2.main;

import com.practica2.paralelismo.GestorParalelo;
import com.practica2.utils.GestorImagenes;
import com.practica2.utils.GestorImagenes.ImagenCargada;
import com.practica2.utils.RegistradorTiempos;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * ============================================================================
 * CLASE PRINCIPAL: PROCESAMIENTO EN LOTE (BATCH) Y BENCHMARK PARALELO (HPC)
 * ============================================================================
 * Proyecto: Detección de bordes con Filtro Sobel 3x3 usando Java Threads.
 *
 * ARQUITECTURA MODULAR:
 * - FiltroSobel: Kernel de convolución Sobel 3x3 y cálculo de gradientes.
 * - HiloSobel: Worker Thread para procesamiento concurrente de franjas.
 * - GestorParalelo: Descomposición de dominio, fork-join, balanceo de carga y
 * benchmark.
 * - GestorImagenes: Lectura/escritura en disco con medición I/O y generación
 * sintética.
 * - RegistradorTiempos: Métricas (Speedup, Eficiencia, Amdahl, Gustafson) y
 * reporte CSV.
 * ============================================================================
 */
public class Main {

    private static final String CARPETA_ENTRADA = "imagenes/entrada";
    private static final String CARPETA_SALIDA = "imagenes/salida";
    private static final String RUTA_CSV = "resultados/metricas_rendimiento.csv";

    private static final int ANCHO_DEFAULT_4K = 3840;
    private static final int ALTO_DEFAULT_4K = 2160;

    public static void main(String[] args) {
        RegistradorTiempos.imprimirEncabezadoSistema();
        RegistradorTiempos.imprimirInformacionHardware();

        try {
            // 1. Detección y aseguramiento de la colección de imágenes
            List<File> listaImagenes = GestorImagenes.obtenerOAsegurarImagenes(
                    CARPETA_ENTRADA, ANCHO_DEFAULT_4K, ALTO_DEFAULT_4K);
            RegistradorTiempos.imprimirColeccionImagenes(listaImagenes, CARPETA_ENTRADA);

            RegistradorTiempos registrador = new RegistradorTiempos();
            Set<Integer> configuracionesHilos = GestorParalelo.obtenerConfiguracionesHilos();

            int contador = 1;
            // 2. Procesamiento en lote (batch) de cada imagen
            for (File archivoImagen : listaImagenes) {
                String nombreCompleto = archivoImagen.getName();
                String nombreBase = GestorImagenes.obtenerNombreSinExtension(nombreCompleto);
                String extensionOriginal = GestorImagenes.obtenerExtension(nombreCompleto, "jpg");
                String formatoSalida = GestorImagenes.obtenerFormatoEscrituraValido(extensionOriginal);

                // Medición de lectura I/O de la imagen
                ImagenCargada carga = GestorImagenes.cargarImagenMedida(archivoImagen.getAbsolutePath());
                BufferedImage imagenOrigen = carga.getImagen();
                double tiempoLecturaMs = carga.getTiempoLecturaMs();

                int ancho = imagenOrigen.getWidth();
                int alto = imagenOrigen.getHeight();
                String resolucion = ancho + "x" + alto;
                double tamanioRamMb = GestorImagenes.calcularTamanioRamMb(ancho, alto);

                RegistradorTiempos.imprimirEncabezadoImagen(
                        contador++, listaImagenes.size(), nombreCompleto, extensionOriginal,
                        resolucion, tamanioRamMb, tiempoLecturaMs);

                // --- PROCESAMIENTO SECUENCIAL (Línea Base: p = 1) ---
                System.out.println("  -> Ejecutando versión SECUENCIAL (p = 1 hilo)...");
                BufferedImage imagenDestinoSecuencial = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
                double ts = GestorParalelo.procesarSecuencial(imagenOrigen, imagenDestinoSecuencial);
                System.out.printf("     Tiempo de cómputo Ts: %.2f ms%n", ts);

                registrador.agregarRegistro(nombreCompleto, resolucion, 1, ts, ts);

                // --- PROCESAMIENTO PARALELO MULTIHILO ---
                BufferedImage imagenDestinoFinal = GestorParalelo.ejecutarPruebasParalelas(
                        imagenOrigen, nombreCompleto, resolucion, ts, configuracionesHilos, registrador);

                // Guardado exclusivo de la última imagen procesada
                double tiempoEscrituraMs = 0.0;
                if (imagenDestinoFinal != null) {
                    String rutaSalida = CARPETA_SALIDA + "/" + nombreBase + "_sobel." + formatoSalida;
                    tiempoEscrituraMs = GestorImagenes.guardarImagenMedida(
                            imagenDestinoFinal, rutaSalida, formatoSalida);
                    System.out.printf("     [SALIDA] Imagen final guardada en: %s (Tiempo I/O Escritura: %.2f ms)%n",
                            rutaSalida, tiempoEscrituraMs);
                }

                registrador.imprimirTablaConsola(nombreCompleto);
            }

            // 3. Resumen global consolidado y exportación de métricas
            registrador.imprimirTablaConsolidadaGlobal();
            registrador.exportarCSV(RUTA_CSV);

            System.out.println("\n>>> PROCESAMIENTO DE TODAS LAS IMÁGENES COMPLETADO CON ÉXITO <<<");

        } catch (Exception e) {
            System.err.println("Error durante el procesamiento por lotes: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

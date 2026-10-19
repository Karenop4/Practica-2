package com.practica2.algoritmo;

import java.awt.image.BufferedImage;

/**
 * ============================================================================
 * CONCEPTO TEÓRICO: KERNEL DE CONVOLUCIÓN Y OPERADOR SOBEL (HPC)
 * ============================================================================
 * El algoritmo de Sobel calcula una aproximación del gradiente espacial de la
 * intensidad de una imagen en dos direcciones ortogonales:
 *   - Gx: detecta variaciones horizontales (bordes verticales).
 *   - Gy: detecta variaciones verticales (bordes horizontales).
 *
 * PARALELISMO DE DATOS Y MEMORIA COMPARTIDA:
 * - Todos los hilos leen simultáneamente de la imagen 'origen' compartida en el Heap.
 *   Al ser una operación de solo lectura, no requiere cerrojos (lock-free).
 * - Cada hilo escribe exclusivamente en su propio rango de filas [filaInicio, filaFin)
 *   en la imagen 'destino', evitando condiciones de carrera (Race Conditions).
 * ============================================================================
 */
public class FiltroSobel {

    // Máscara horizontal de Sobel (Gx) para detectar bordes verticales
    private static final int[][] SOBEL_X = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };

    // Máscara vertical de Sobel (Gy) para detectar bordes horizontales
    private static final int[][] SOBEL_Y = {
        {-1, -2, -1},
        { 0,  0,  0},
        { 1,  2,  1}
    };

    /**
     * Aplica el operador Sobel secuencialmente a la totalidad de la imagen.
     *
     * @param origen  Imagen original en memoria (solo lectura).
     * @param destino Imagen donde se escribirán los bordes resultantes.
     */
    public static void aplicarSobel(BufferedImage origen, BufferedImage destino) {
        aplicarSobel(origen, destino, 0, origen.getHeight());
    }

    /**
     * Aplica el operador Sobel en un subdominio de filas [filaInicio, filaFin).
     *
     * @param origen     Imagen original en memoria compartida (solo lectura).
     * @param destino    Imagen donde se escriben los bordes resultantes.
     * @param filaInicio Fila inicial asignada a este hilo (inclusiva).
     * @param filaFin    Fila final asignada a este hilo (exclusiva).
     */
    public static void aplicarSobel(BufferedImage origen, BufferedImage destino, int filaInicio, int filaFin) {
        int ancho = origen.getWidth();
        int alto = origen.getHeight();

        // ====================================================================
        // CONCEPTO TEÓRICO: ZONA DE SOLAPAMIENTO / HALO / CELDAS FANTASMA (GHOST CELLS)
        // ====================================================================
        // Cada sección procesa un bloque propio de filas [filaInicio, filaFin).
        // Sin embargo, debido a que el kernel de Sobel es de 3x3, para calcular la
        // fila frontera 'filaInicio' se necesita leer obligatoriamente la fila
        // 'filaInicio - 1' (perteneciente a la sección del hilo vecino superior).
        // De igual forma, para calcular 'filaFin - 1', se necesita leer la fila
        // 'filaFin' (perteneciente a la sección del hilo vecino inferior).
        // 
        // - Dominio de ESCRITURA exclusivo de esta sección: [filaInicio, filaFin)
        // - Dominio de LECTURA con solapamiento (Halo):     [filaInicio - 1, filaFin + 1)
        // ====================================================================

        // Delimitamos las filas seguras de escritura para la imagen completa (evitando bordes globales y=0 e y=alto-1)
        int yInicioSeguro = Math.max(1, filaInicio);
        int yFinSeguro = Math.min(alto - 1, filaFin);

        // Bucle externo: recorre la porción de filas asignadas a esta sección
        for (int y = yInicioSeguro; y < yFinSeguro; y++) {
            // Bucle interno: recorre las columnas evitando los bordes globales x = 0 y x = ancho - 1
            for (int x = 1; x < ancho - 1; x++) {

                int sumX = 0;
                int sumY = 0;

                // Convolución espacial con el vecindario 3x3
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        // Extracción del píxel vecino en memoria compartida
                        int pixelRgb = origen.getRGB(x + kx, y + ky);

                        // Extracción manual de canales RGB con operadores a nivel de bits
                        int r = (pixelRgb >> 16) & 0xFF;
                        int g = (pixelRgb >> 8) & 0xFF;
                        int b = pixelRgb & 0xFF;

                        // Conversión a escala de grises mediante promedio aritmético
                        int gris = (r + g + b) / 3;

                        // Multiplicación acumulativa por los pesos de las matrices Sobel
                        int pesoX = SOBEL_X[ky + 1][kx + 1];
                        int pesoY = SOBEL_Y[ky + 1][kx + 1];

                        sumX += gris * pesoX;
                        sumY += gris * pesoY;
                    }
                }

                // Magnitud del gradiente euclidiano: sqrt(sumX^2 + sumY^2) acotada a [0, 255]
                int magnitud = (int) Math.min(255, Math.sqrt(sumX * sumX + sumY * sumY));

                // Empaquetado del valor en formato ARGB (Alpha = 255 opaco)
                int nuevoRgb = (255 << 24) | (magnitud << 16) | (magnitud << 8) | magnitud;

                // Escritura en la imagen destino (independiente por hilo, sin conflicto de memoria)
                destino.setRGB(x, y, nuevoRgb);
            }
        }
    }
}

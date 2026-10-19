package com.practica2.utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * ============================================================================
 * UTILIDADES: GESTOR DE ENTRADA Y SALIDA (I/O) DE IMÁGENES
 * ============================================================================
 * En el análisis de rendimiento de computación paralela (Ley de Amdahl), las
 * operaciones de lectura y escritura en disco representan el "cuello de
 * botella"
 * secuencial estrictamente no paralelizable:
 * Tiempo Total = T_secuencial_IO + (T_computo / p)
 * ============================================================================
 */
public class GestorImagenes {

    /**
     * Encapsula una imagen cargada junto con el tiempo medido de lectura desde
     * disco.
     */
    public static class ImagenCargada {
        private final BufferedImage imagen;
        private final double tiempoLecturaMs;

        public ImagenCargada(BufferedImage imagen, double tiempoLecturaMs) {
            this.imagen = imagen;
            this.tiempoLecturaMs = tiempoLecturaMs;
        }

        public BufferedImage getImagen() {
            return imagen;
        }

        public double getTiempoLecturaMs() {
            return tiempoLecturaMs;
        }
    }

    /**
     * Escanea y lista todas las imágenes presentes en el directorio indicado,
     * detectando cualquier formato soportado por la JVM (JPG, PNG, BMP, GIF, etc.)
     * e ignorando mayúsculas/minúsculas y archivos ocultos.
     *
     * @param rutaCarpeta Ruta del directorio a explorar.
     * @return Lista de archivos de imagen válidos ordenada alfabéticamente.
     */
    public static List<File> listarImagenes(String rutaCarpeta) {
        File carpeta = new File(rutaCarpeta);
        List<File> listaImagenes = new ArrayList<>();

        if (!carpeta.exists() || !carpeta.isDirectory()) {
            return listaImagenes;
        }

        File[] archivos = carpeta.listFiles();
        if (archivos == null) {
            return listaImagenes;
        }

        // Obtener extensiones de archivo compatibles con ImageIO
        Set<String> formatosValidos = new HashSet<>();
        for (String sufijo : ImageIO.getReaderFileSuffixes()) {
            formatosValidos.add(sufijo.toLowerCase());
        }

        for (File f : archivos) {
            if (f.isFile() && !f.isHidden()) {
                String extension = obtenerExtension(f.getName(), "").toLowerCase();
                if (formatosValidos.contains(extension)) {
                    listaImagenes.add(f);
                }
            }
        }

        listaImagenes.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return listaImagenes;
    }

    /**
     * Obtiene las imágenes existentes en la carpeta o genera automáticamente una
     * muestra 4K de prueba si está vacía.
     *
     * @param rutaCarpeta  Ruta del directorio de entrada.
     * @param anchoMuestra Ancho de la imagen de muestra en píxeles.
     * @param altoMuestra  Alto de la imagen de muestra en píxeles.
     * @return Lista de archivos de imagen garantizada (no vacía).
     * @throws IOException Si ocurre un error al persistir la imagen de prueba.
     */
    public static List<File> obtenerOAsegurarImagenes(String rutaCarpeta, int anchoMuestra, int altoMuestra)
            throws IOException {
        List<File> lista = listarImagenes(rutaCarpeta);
        if (lista.isEmpty()) {
            System.out.println("[INFO] No se encontraron imágenes en '" + rutaCarpeta + "'.");
            System.out.println("[INFO] Generando automáticamente muestra 4K de prueba...");
            String rutaMuestra = rutaCarpeta + "/muestra_4K.jpg";
            generarImagenPrueba(anchoMuestra, altoMuestra, rutaMuestra);
            lista = listarImagenes(rutaCarpeta);
        }
        return lista;
    }

    /**
     * Extrae el nombre base de un archivo descartando su extensión.
     */
    public static String obtenerNombreSinExtension(String nombreArchivo) {
        int idx = nombreArchivo.lastIndexOf('.');
        return (idx > 0) ? nombreArchivo.substring(0, idx) : nombreArchivo;
    }

    /**
     * Extrae la extensión del nombre del archivo.
     */
    public static String obtenerExtension(String nombreArchivo, String extensionPorDefecto) {
        int idx = nombreArchivo.lastIndexOf('.');
        if (idx > 0 && idx < nombreArchivo.length() - 1) {
            return nombreArchivo.substring(idx + 1).toLowerCase();
        }
        return extensionPorDefecto;
    }

    /**
     * Determina el formato de escritura compatible con ImageIO para guardar la
     * imagen.
     * Si el formato original no dispone de un escritor registrado, usa "jpg" como
     * estándar seguro.
     */
    public static String obtenerFormatoEscrituraValido(String extensionOriginal) {
        String ext = extensionOriginal.toLowerCase();
        for (String escritor : ImageIO.getWriterFileSuffixes()) {
            if (escritor.equalsIgnoreCase(ext)) {
                return ext;
            }
        }
        return "jpg";
    }

    /**
     * Calcula el tamaño estimado en memoria RAM en Megabytes (MB) para una imagen
     * TYPE_INT_RGB (4 bytes por píxel).
     */
    public static double calcularTamanioRamMb(int ancho, int alto) {
        return (ancho * alto * 4.0) / (1024 * 1024);
    }

    /**
     * Carga una imagen desde el disco.
     * Convierte la imagen cargada a formato estándar TYPE_INT_RGB para garantizar
     * compatibilidad homogénea de canales y máximo rendimiento en operaciones de
     * bits.
     *
     * @param ruta Ruta del archivo de imagen a cargar.
     * @return BufferedImage lista para su procesamiento en memoria.
     * @throws IOException Si ocurre un error de lectura o el archivo no existe.
     */
    public static BufferedImage cargarImagen(String ruta) throws IOException {
        File archivo = new File(ruta);
        if (!archivo.exists()) {
            throw new IOException("El archivo de imagen no existe en la ruta: " + archivo.getAbsolutePath());
        }

        BufferedImage original = ImageIO.read(archivo);
        if (original == null) {
            throw new IOException("No se pudo decodificar el formato de imagen: " + ruta);
        }

        // Normalizamos a BufferedImage.TYPE_INT_RGB para acceso coherente de píxeles
        BufferedImage imagenNormalizada = new BufferedImage(
                original.getWidth(),
                original.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = imagenNormalizada.createGraphics();
        g2d.drawImage(original, 0, 0, null);
        g2d.dispose();

        return imagenNormalizada;
    }

    /**
     * Carga una imagen midiendo el tiempo que toma la operación de lectura I/O
     * desde disco.
     *
     * @param ruta Ruta del archivo a cargar.
     * @return Objeto ImagenCargada conteniendo la imagen y el tiempo transcurrido
     *         en ms.
     * @throws IOException Si ocurre un fallo al acceder o decodificar el archivo.
     */
    public static ImagenCargada cargarImagenMedida(String ruta) throws IOException {
        long tInicio = System.nanoTime();
        BufferedImage imagen = cargarImagen(ruta);
        long tFin = System.nanoTime();
        double tiempoMs = (tFin - tInicio) / 1_000_000.0;
        return new ImagenCargada(imagen, tiempoMs);
    }

    /**
     * Guarda una imagen en el disco en el formato especificado.
     * Crea automáticamente todos los directorios contenedores si no existen.
     *
     * @param imagen  BufferedImage a guardar.
     * @param ruta    Ruta de destino en disco.
     * @param formato Formato de imagen ("jpg", "png", etc.).
     * @throws IOException Si ocurre un error durante la escritura en disco.
     */
    public static void guardarImagen(BufferedImage imagen, String ruta, String formato) throws IOException {
        File archivoSalida = new File(ruta);
        File directorioPadre = archivoSalida.getParentFile();

        // Creación automática de carpetas jerárquicas si no existen
        if (directorioPadre != null && !directorioPadre.exists()) {
            directorioPadre.mkdirs();
        }

        boolean exito = ImageIO.write(imagen, formato, archivoSalida);
        if (!exito) {
            // Reintento con formato JPG como alternativa segura universal
            exito = ImageIO.write(imagen, "jpg", archivoSalida);
            if (!exito) {
                throw new IOException("No se encontró un escritor adecuado para el formato '" + formato + "'.");
            }
        }
    }

    /**
     * Guarda una imagen en disco midiendo con precisión el tiempo transcurrido en
     * I/O.
     *
     * @param imagen  BufferedImage a persistir.
     * @param ruta    Ruta de destino en disco.
     * @param formato Formato ("jpg", "png", etc.).
     * @return Tiempo de escritura medido en milisegundos.
     * @throws IOException Si ocurre un error de escritura.
     */
    public static double guardarImagenMedida(BufferedImage imagen, String ruta, String formato) throws IOException {
        long tInicio = System.nanoTime();
        guardarImagen(imagen, ruta, formato);
        long tFin = System.nanoTime();
        return (tFin - tInicio) / 1_000_000.0;
    }

    /**
     * Genera sintéticamente una imagen de prueba de alta resolución (ej. 4K:
     * 3840x2160)
     * con una rica variedad de bordes geométricos, degradados y patrones
     * concéntricos.
     * Permite que el proyecto funcione de inmediato de manera autónoma sin requerir
     * descargas previas.
     *
     * @param ancho Ancho en píxeles (ej. 3840).
     * @param alto  Alto en píxeles (ej. 2160).
     * @param ruta  Ruta donde se guardará la imagen generada.
     * @throws IOException Si ocurre un error al persistir la imagen generada.
     */
    public static void generarImagenPrueba(int ancho, int alto, String ruta) throws IOException {
        System.out.println("  [I/O] Generando imagen de muestra 4K (" + ancho + "x" + alto + ")...");
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Fondo degradado
        for (int y = 0; y < alto; y++) {
            float ratio = (float) y / alto;
            int r = (int) (30 + 180 * ratio);
            int gr = (int) (50 + 120 * (1 - ratio));
            int b = (int) (100 + 155 * Math.sin(ratio * Math.PI));
            g.setColor(new Color(r, gr, b));
            g.drawLine(0, y, ancho, y);
        }

        // 2. Cuadrícula de contraste alto
        g.setColor(new Color(240, 240, 240));
        int espaciado = 120;
        for (int x = 0; x < ancho; x += espaciado) {
            g.drawLine(x, 0, x, alto);
        }
        for (int y = 0; y < alto; y += espaciado) {
            g.drawLine(0, y, ancho, y);
        }

        // 3. Círculos concéntricos
        int centroX = ancho / 2;
        int centroY = alto / 2;
        for (int radio = 100; radio < 900; radio += 60) {
            g.setColor((radio % 120 == 0) ? Color.WHITE : Color.BLACK);
            g.drawOval(centroX - radio, centroY - radio, radio * 2, radio * 2);
        }

        // 4. Formas geométricas
        g.setColor(new Color(255, 215, 0));
        g.fillRect(300, 300, 600, 400);

        g.setColor(new Color(220, 20, 60));
        g.fillOval(ancho - 1000, 300, 500, 500);

        g.setColor(new Color(0, 191, 255));
        int[] triX = { ancho / 2, (ancho / 2) - 400, (ancho / 2) + 400 };
        int[] triY = { alto - 700, alto - 200, alto - 200 };
        g.fillPolygon(triX, triY, 3);

        // 5. Rótulo de texto con alto contraste
        g.setFont(new Font("SansSerif", Font.BOLD, 110));
        g.setColor(Color.WHITE);
        g.drawString("HPC - COMPUTACIÓN PARALELA", 350, alto / 2 - 100);
        g.setFont(new Font("SansSerif", Font.PLAIN, 70));
        g.setColor(Color.YELLOW);
        g.drawString("Filtro Sobel 3x3 - Detección de Bordes (4K Benchmark)", 350, alto / 2 + 20);

        g.dispose();

        guardarImagen(imagen, ruta, "jpg");
        System.out.println("  [I/O] Imagen de muestra 4K creada exitosamente en: " + ruta);
    }
}

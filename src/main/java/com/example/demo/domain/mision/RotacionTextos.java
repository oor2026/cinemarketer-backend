package com.example.demo.domain.mision;

/**
 * Los textos y las metas de las misiones rotativas. Es lo que usa el catálogo automático para crear la misión y el sello de cada
 * objetivo (la saga de Harry Potter, Meryl Streep, Christopher Nolan, el terror...). Es Java puro: se prueba solo.
 * Las metas de actor/actriz, director y género son fijas; la de una saga depende de la saga (sus películas ya estrenadas).
 */
public final class RotacionTextos {
    private RotacionTextos() {}

    public static final int META_ACTOR = 5;
    public static final int META_DIRECTOR = 5;
    public static final int META_GENERO = 10;

    /** Largos que admite la base: nombre del objetivo 120, título de la misión 100 y nombre del sello 80 (se deja de margen). */
    public static final int MAX_NOMBRE = 100, MAX_TITULO = 80;

    /** Recorta con «…» si hace falta. Siempre da el mismo resultado para el mismo texto (así no figura como «cambiado» en cada corrida). */
    public static String recortar(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1).stripTrailing() + "…";
    }

    /** El nombre tal como se guarda: sin espacios de más y con un largo que entra en la base. */
    public static String nombre(String s) { return recortar(s.trim().replaceAll("\\s+", " "), MAX_NOMBRE); }

    /**
     * El nombre de una saga tal como lo muestra la misión. TMDB suele llamar a las colecciones «Harry Potter - Colección» o «Toy Story Collection»;
     * acá se les saca la palabra colección/collection/saga, para que la misión diga «Saga Harry Potter» y no «Saga Harry Potter - Colección».
     */
    public static String nombreSaga(String s) {
        String x = s.trim();
        String limpio = x.replaceAll("(?i)\\s*[-–:]?\\s*(colecci[oó]n|collection|saga)\\s*$", "")
                .replaceAll("(?i)^(colecci[oó]n|saga)\\s+(de\\s+la\\s+|de\\s+los\\s+|de\\s+las\\s+|del\\s+|de\\s+)?", "").trim();
        return nombre(limpio.length() < 3 ? x : limpio);                // si no queda nada razonable, se deja el nombre original
    }

    /** El nombre de la misión y de su sello: «Saga Harry Potter», «Fan de Meryl Streep», «Cine de Christopher Nolan», «Maratón de terror». */
    public static String titulo(String familia, String nombre) {
        return switch (familia) {
            case "SAGA" -> "Saga " + nombre;
            case "ACTOR" -> "Fan de " + nombre;
            case "DIRECTOR" -> "Cine de " + nombre;
            default -> "Maratón de " + nombre.toLowerCase();
        };
    }

    public static String descripcion(String familia, int meta, String nombre) {
        return switch (familia) {
            case "SAGA" -> "Votá las " + meta + " películas de la saga " + nombre + ".";
            case "ACTOR" -> "Votá " + meta + " películas con " + nombre + ".";
            case "DIRECTOR" -> "Votá " + meta + " películas de " + nombre + ".";
            default -> "Votá " + meta + " películas de " + nombre.toLowerCase() + ".";
        };
    }

    public static String cumplida(String familia, int meta, String nombre) {
        return switch (familia) {
            case "SAGA" -> "Votaste las " + meta + " películas de la saga " + nombre + ".";
            case "ACTOR" -> "Votaste " + meta + " películas con " + nombre + ".";
            case "DIRECTOR" -> "Votaste " + meta + " películas de " + nombre + ".";
            default -> "Votaste " + meta + " películas de " + nombre.toLowerCase() + ".";
        };
    }

    /** La categoría del sello (y de la misión), que define su color y su ícono. */
    public static String categoria(String familia) {
        return switch (familia) { case "SAGA" -> "saga"; case "ACTOR" -> "elenco"; case "DIRECTOR" -> "autor"; default -> "genero"; };
    }
}

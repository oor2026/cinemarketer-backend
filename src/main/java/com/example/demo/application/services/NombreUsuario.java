package com.example.demo.application.services;

import java.util.regex.Pattern;

/**
 * Reglas del nombre de una persona: el que se muestra en rankings, comentarios, novedades y perfiles.
 *
 * Por qué existe: al registrarse, RegisterRequest ya exige un nombre de 2 a 50 caracteres con solo letras y espacios
 * simples entre palabras. Pero editar el nombre desde Mi cuenta (PATCH /api/users/me) no tenía ninguna de esas
 * reglas, y el login con Google toma el nombre de la cuenta de Google tal cual. Un nombre con "<" o con comillas
 * puede romper la pantalla de quien lo ve, o ejecutar código en su navegador. Esta clase deja la regla en un solo
 * lugar para que todas las puertas por las que entra un nombre usen la misma.
 */
public final class NombreUsuario {

    public static final int MIN = 2;
    public static final int MAX = 50;

    // La misma regla que RegisterRequest: letras (con tildes, ü y ñ) y espacios simples entre palabras.
    private static final Pattern FORMATO =
            Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+(\\s[a-zA-ZáéíóúÁÉÍÓÚüÜñÑ]+)*$");

    // Para limpiar nombres que vienen de afuera (Google): letras de cualquier idioma, sus marcas, espacios,
    // guiones y puntos. Todo lo demás (etiquetas, comillas, paréntesis, símbolos, números) se descarta.
    private static final Pattern NO_PERMITIDO = Pattern.compile("[^\\p{L}\\p{M} .\\-]");

    private NombreUsuario() {
    }

    /**
     * Devuelve null si el nombre es válido, o el mensaje para mostrarle a la persona si no lo es.
     * Las reglas son las del registro. Al equipo de Cinemarketer (admin) se le permite además el ® de la marca.
     */
    public static String errorDe(String nombre, boolean admiteMarcaDelEquipo) {
        if (nombre == null || nombre.isBlank()) {
            return "El nombre no puede estar vacío.";
        }
        String n = nombre.trim();
        if (n.length() < MIN || n.length() > MAX) {
            return "El nombre debe tener entre " + MIN + " y " + MAX + " caracteres.";
        }
        String base = admiteMarcaDelEquipo ? n.replace("®", "") : n;
        if (!FORMATO.matcher(base).matches()) {
            return "El nombre solo puede contener letras y espacios simples entre palabras.";
        }
        return null;
    }

    /**
     * Limpia un nombre que viene de afuera, donde no se puede pedir que lo cambien: descarta todo lo que no sea
     * letra, espacio, guion o punto, junta los espacios repetidos y lo acorta al máximo. Si no queda un nombre
     * utilizable, devuelve el de respaldo.
     */
    public static String limpiar(String nombre, String respaldo) {
        // Primero cualquier separador (tab, salto de línea, espacio no cortante...) pasa a ser un espacio común,
        // para que no queden palabras pegadas; después se descarta lo que no está permitido.
        String n = nombre == null ? "" : NO_PERMITIDO.matcher(nombre.replaceAll("[\\s\\p{Z}]+", " ")).replaceAll("");
        n = n.replaceAll(" {2,}", " ").trim();
        if (n.length() > MAX) {
            n = n.substring(0, MAX).trim();
        }
        return n.length() < MIN ? respaldo : n;
    }
}
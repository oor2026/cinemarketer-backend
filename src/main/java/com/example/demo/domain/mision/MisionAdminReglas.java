package com.example.demo.domain.mision;

import java.util.*;

/**
 * Las reglas de qué se puede pedir en una misión: qué tipos hay y qué opciones admite cada uno. Es la ÚNICA fuente de verdad: el validador la usa
 * para rechazar combinaciones imposibles y el asistente del panel la pide al servidor para ofrecer solo lo que tiene sentido.
 */
public final class MisionAdminReglas {
    private MisionAdminReglas() {}

    /**
     * Un tipo de misión. tipoContenido = admite elegir película o serie · filtrosPelicula = admite género, director, saga y actor/actriz (solo películas) ·
     * distintas = admite «contar distintos» · vigencias = las que tienen sentido · escalera = admite niveles · metaMax = tope razonable de la meta.
     */
    public record Metrica(String codigo, String etiqueta, String ayuda, String unidad, boolean tipoContenido, boolean filtrosPelicula, boolean distintas,
                          List<String> vigencias, boolean escalera, int metaMax) {}

    private static final List<String> CON_CICLOS = List.of("HOY", "SEMANA", "PERMANENTE", "EVENTO");
    private static final List<String> SIN_CICLOS = List.of("PERMANENTE", "EVENTO");

    public static final List<Metrica> METRICAS = List.of(
            new Metrica("VOTOS", "Votar películas o series", "Cada voto nuevo suma 1. Un voto cuenta una sola vez por título.", "votos", true, true, false, CON_CICLOS, true, 5000),
            new Metrica("COMENTARIOS", "Comentar", "Cada comentario publicado suma 1.", "comentarios", true, true, true, CON_CICLOS, true, 5000),
            new Metrica("RACHA_DIAS", "Racha de días", "Días seguidos con actividad (votar o comentar). Si se corta, el progreso vuelve a 0.", "días", false, false, false, List.of("RACHA"), true, 1000),
            new Metrica("TRIVIAS", "Acertar trivias", "Cada respuesta correcta suma 1.", "trivias", true, false, false, CON_CICLOS, true, 5000),
            new Metrica("PUBLICACIONES", "Publicar en la comunidad", "Cada publicación aprobada suma 1.", "publicaciones", false, false, false, CON_CICLOS, true, 1000),
            new Metrica("INTERVENCIONES", "Participar en publicaciones de otros", "Cada comentario o respuesta en una publicación ajena suma 1.", "intervenciones", false, false, true, CON_CICLOS, true, 5000),
            new Metrica("CANJES", "Canjear premios", "Cada premio canjeado suma 1.", "premios", false, false, true, SIN_CICLOS, true, 100),
            new Metrica("SORTEOS", "Anotarse a sorteos", "Cada sorteo en el que se anota suma 1. Hoy solo los Premium pueden anotarse.", "sorteos", false, false, false, SIN_CICLOS, true, 100),
            new Metrica("GUSTOS", "Completar Mis gustos", "Cada espacio de gustos que guarda suma 1 (una sola vez por espacio). Hay 4 de películas y 4 de series.", "gustos", true, false, false, List.of("PERMANENTE"), false, 8),
            new Metrica("RECOMENDACIONES", "Recomendar a amigos", "Cada recomendación enviada suma 1.", "recomendaciones", true, true, true, CON_CICLOS, true, 1000));

    public static Optional<Metrica> metrica(String codigo) {
        return METRICAS.stream().filter(m -> m.codigo().equals(codigo)).findFirst();
    }

    public static final List<String> CATEGORIAS = List.of("votos", "comentarios", "constancia", "genero", "autor", "trivia", "comunidad", "especial", "saga", "elenco", "premios", "perfil", "recomendaciones");
    public static final List<String> VIGENCIAS = List.of("HOY", "SEMANA", "PERMANENTE", "RACHA", "EVENTO");
    public static final List<String> VISIBILIDADES = List.of("VISIBLE", "MISTERIOSA", "OCULTA");
    public static final List<String> AUDIENCIAS = List.of("TODOS", "PREMIUM", "FREE");
    public static final List<String> DIFICULTADES = List.of("FACIL", "MEDIA", "DIFICIL");

    /** A dónde puede llevar el botón de una misión (rutas reales de la aplicación). Para sumar una, se agrega acá. */
    public static final Map<String, String> DESTINOS = new LinkedHashMap<>();
    static {
        DESTINOS.put("feed-films", "Inicio (películas)");
        DESTINOS.put("cartelera", "Cartelera");
        DESTINOS.put("comunidad", "Comunidad");
        DESTINOS.put("club-beneficios", "Club de Beneficios");
        DESTINOS.put("mi-lista", "Mi Lista");
    }

    /** Fácil = 1, media = 2, difícil = 3: cuánto «pesa» cada paso al elegir cuál es la misión más fácil de cumplir. */
    public static double esfuerzo(String dificultad) {
        return switch (dificultad == null ? "MEDIA" : dificultad) { case "FACIL" -> 1.0; case "DIFICIL" -> 3.0; default -> 2.0; };
    }

    public static String dificultadDe(double esfuerzo) { return esfuerzo <= 1.5 ? "FACIL" : esfuerzo >= 2.5 ? "DIFICIL" : "MEDIA"; }

    /** La categoría (color e ícono del sello) que mejor describe la misión, si el admin no eligió una. */
    public static String categoriaPorDefecto(String metrica, boolean saga, boolean actor, boolean director, boolean genero) {
        if (saga) return "saga";
        if (actor) return "elenco";
        if (director) return "autor";
        if (genero) return "genero";
        return switch (metrica == null ? "" : metrica) {
            case "VOTOS" -> "votos"; case "COMENTARIOS" -> "comentarios"; case "RACHA_DIAS" -> "constancia"; case "TRIVIAS" -> "trivia";
            case "PUBLICACIONES", "INTERVENCIONES" -> "comunidad"; case "CANJES", "SORTEOS" -> "premios"; case "GUSTOS" -> "perfil";
            case "RECOMENDACIONES" -> "recomendaciones"; default -> "especial";
        };
    }
}

package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionAdminValidador.Problema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte lo que manda el panel (un JSON ya leído como mapa) en un {@link MisionAdminForm}. Es TOLERANTE: acepta números como texto
 * («10»), fechas con o sin hora, listas de metas separadas por coma y mayúsculas o minúsculas, y NUNCA tira una excepción: lo que no se
 * puede leer vuelve como un problema, atado al campo que corresponde. Qué combinaciones valen lo decide {@link MisionAdminValidador}.
 */
public final class MisionAdminFormParser {

    private MisionAdminFormParser() {}

    public record Parseo(MisionAdminForm form, List<Problema> problemas) {
        public boolean ok() { return problemas.isEmpty(); }
    }

    public static Parseo parsear(Map<String, ?> m) {
        Map<String, ?> x = m == null ? Map.of() : m;
        List<Problema> p = new ArrayList<>();
        MisionAdminForm f = new MisionAdminForm(
                mayus(x, "metrica"), mayus(x, "tipoContenido"),
                largo(x, "generoId", p), largo(x, "directorId", p), largo(x, "sagaId", p), largo(x, "actorId", p), bool(x, "distintas"),
                entero(x, "meta", p), mayus(x, "vigencia"), fecha(x, "desde", false, p), fecha(x, "hasta", true, p),
                enteros(x, "escalera", p), entero(x, "escaleraPaso", p), mayus(x, "escaleraSobre"),
                texto(x, "titulo"), texto(x, "descripcion"), texto(x, "descripcionCumplida"), minus(x, "categoria"),
                largo(x, "selloExistenteId", p), entero(x, "nivelSello", p),
                texto(x, "selloNombre"), texto(x, "selloDescripcion"), url(x, "selloImagenUrl"), minus(x, "selloCategoria"),
                mayus(x, "visibilidad"), entero(x, "sellosNecesarios", p), largo(x, "prerrequisitoId", p), mayus(x, "audiencia"),
                mayus(x, "dificultad"), entero(x, "orden", p), texto(x, "accionTexto"), destino(x));
        return new Parseo(f, p);
    }

    // ------------------------------------------------------------------------------------------------ textos
    private static String texto(Map<String, ?> m, String k) {
        Object o = m.get(k);
        if (o == null) return null;
        String s = o.toString().trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    private static String mayus(Map<String, ?> m, String k) { String s = texto(m, k); return s == null ? null : s.toUpperCase(Locale.ROOT); }

    private static String minus(Map<String, ?> m, String k) { String s = texto(m, k); return s == null ? null : s.toLowerCase(Locale.ROOT); }

    /** Las URL no se tocan por dentro (sin colapsar espacios ni cambiar mayúsculas), solo se recortan. */
    private static String url(Map<String, ?> m, String k) {
        Object o = m.get(k);
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** Las pantallas se guardan sin el «#» (así las espera la lista de destinos); si el panel lo manda, se lo saca. */
    private static String destino(Map<String, ?> m) {
        String s = texto(m, "accionDestino");
        if (s == null) return null;
        s = s.startsWith("#") ? s.substring(1) : s;
        return s.isEmpty() ? null : s;
    }

    private static Boolean bool(Map<String, ?> m, String k) {
        Object o = m.get(k);
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        String s = o.toString().trim();
        return s.isEmpty() ? null : "true".equalsIgnoreCase(s);
    }

    // ------------------------------------------------------------------------------------------------ números
    private static Integer entero(Map<String, ?> m, String k, List<Problema> p) {
        Long l = largo(m, k, p);
        if (l == null) return null;
        if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) { p.add(new Problema(k, "El número de «" + k + "» es demasiado grande.")); return null; }
        return l.intValue();
    }

    private static Long largo(Map<String, ?> m, String k, List<Problema> p) {
        Object o = m.get(k);
        if (o == null) return null;
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (d != Math.floor(d) || Double.isInfinite(d)) return noEntero(k, p);
            return n.longValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return noEntero(k, p); }
    }

    private static Long noEntero(String k, List<Problema> p) { p.add(new Problema(k, "«" + k + "» tiene que ser un número entero.")); return null; }

    /** Una lista («[10, 25]» o «10, 25, 50»). Vacía = sin escalera. */
    private static List<Integer> enteros(Map<String, ?> m, String k, List<Problema> p) {
        Object o = m.get(k);
        if (o == null) return null;
        List<Object> crudo = new ArrayList<>();
        if (o instanceof Iterable<?> it) it.forEach(crudo::add);
        else for (String s : o.toString().split(",")) if (!s.isBlank()) crudo.add(s.trim());
        if (crudo.isEmpty()) return null;
        List<Integer> r = new ArrayList<>();
        for (Object e : crudo) {
            try {
                double d = e instanceof Number n ? n.doubleValue() : Double.parseDouble(e.toString().trim());
                if (d != Math.floor(d) || Math.abs(d) > Integer.MAX_VALUE) throw new NumberFormatException();
                r.add((int) d);
            } catch (NumberFormatException ex) {
                p.add(new Problema(k, "La lista de metas solo puede tener números enteros (separados por coma)."));
                return null;
            }
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ fechas
    /** Acepta «2026-12-01T20:30», «2026-12-01 20:30» y «2026-12-01» (sin hora: el principio del día para «desde», el final para «hasta»). */
    private static LocalDateTime fecha(Map<String, ?> m, String k, boolean finDelDia, List<Problema> p) {
        String s = texto(m, k);
        if (s == null) return null;
        try {
            if (s.length() <= 10) { LocalDate d = LocalDate.parse(s); return finDelDia ? d.atTime(LocalTime.of(23, 59, 59)) : d.atStartOfDay(); }
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            p.add(new Problema(k, "La fecha no es válida (usá año-mes-día, por ejemplo 2026-12-01)."));
            return null;
        }
    }
}

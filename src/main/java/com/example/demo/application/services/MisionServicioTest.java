package com.example.demo.application.services;

import com.example.demo.domain.mision.*;
import com.example.demo.domain.mision.MisionLogica.*;
import java.time.*;
import java.util.*;

public class MisionServicioTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }

    // ---------------------------------------------------------------------------------------- base en memoria (misma semántica que el SQL)
    static class FilaM { long id, userId, misionId; String ciclo, estado = "EN_CURSO"; int progreso, nivel, veces; LocalDateTime cumplidaAt, vistoAt; }
        static class Cel { long id, userId, misionId; int nivel; String tipo; LocalDateTime at, visto; }
    static class Memoria implements MisionDatos {
        List<Mision> misiones = new ArrayList<>(); List<FilaM> filas = new ArrayList<>(); long seq = 0;
        Map<Long, Map<Long, int[]>> sellos = new HashMap<>(); Map<Long, Map<Long, LocalDateTime>> sellosFecha = new HashMap<>(); Set<String> titulos = new HashSet<>(); Map<Long, Set<LocalDate>> dias = new HashMap<>();
        boolean fallar = false;
        List<Cel> cels = new ArrayList<>(); long seqCel = 0;
        static class Obj { long id, misionId; String familia, nombre; boolean activo = true, excluido = false; }
        static class AsigM { long id, userId, objetivoId, misionId; String familia, estado; }
        List<Obj> objetivos = new ArrayList<>(); List<AsigM> asigs = new ArrayList<>(); long seqAsig = 0;
        Obj objDe(long id) { return objetivos.stream().filter(o -> o.id == id).findFirst().get(); }
        public List<Asignacion> asignaciones(long u) { f(); List<Asignacion> r = new ArrayList<>(); for (AsigM a : asigs) if (a.userId == u) r.add(new Asignacion(a.id, a.objetivoId, a.misionId, a.familia, a.estado, objDe(a.objetivoId).nombre)); return r; }
        public List<Candidato> candidatos(long u, String familia) { f(); List<Candidato> r = new ArrayList<>(); for (Obj o : objetivos) { if (!o.familia.equals(familia) || !o.activo || o.excluido) continue; Mision m = misiones.stream().filter(x -> x.id() == o.misionId).findFirst().orElse(null); if (m == null) continue; if (asigs.stream().anyMatch(a -> a.userId == u && a.objetivoId == o.id)) continue; Map<Long, int[]> s = sellos.getOrDefault(u, Map.of()); if (s.get(m.sello().id()) != null) continue; r.add(new Candidato(o.id, o.misionId)); } return r; }
        public void asignar(long u, String familia, long objId, String estado, LocalDateTime a) { f(); if (asigs.stream().anyMatch(x -> x.userId == u && x.objetivoId == objId)) return; if ((estado.equals("ACTUAL") || estado.equals("SIGUIENTE")) && asigs.stream().anyMatch(x -> x.userId == u && x.familia.equals(familia) && x.estado.equals(estado))) return; AsigM n = new AsigM(); n.id = ++seqAsig; n.userId = u; n.familia = familia; n.objetivoId = objId; n.misionId = objDe(objId).misionId; n.estado = estado; asigs.add(n); }
        public void cambiarEstadoObjetivo(long u, long objId, String estado, LocalDateTime a) { f(); for (AsigM x : asigs) if (x.userId == u && x.objetivoId == objId) x.estado = estado; }
        public void quitarAsignacion(long u, long objId) { f(); asigs.removeIf(x -> x.userId == u && x.objetivoId == objId && x.estado.equals("DESCARTADO")); }
        void f() { if (fallar) throw new IllegalStateException("la base se cayó"); }
        public List<Mision> misionesActivas(long u) { f(); List<Mision> r = new ArrayList<>(); for (Mision x : misiones) if (x.familia() == null || asigs.stream().anyMatch(a -> a.userId == u && a.misionId == x.id())) r.add(x); return r; }
        public Map<Long, Integer> nivelesDeSellos(long u) { f(); Map<Long, Integer> r = new HashMap<>(); sellos.getOrDefault(u, Map.of()).forEach((k, v) -> r.put(k, v[0])); return r; }
        public List<Fila> filas(long u, String d, String s) { f(); List<Fila> r = new ArrayList<>(); for (FilaM x : filas) if (x.userId == u && (x.ciclo.equals("P") || x.ciclo.equals(d) || x.ciclo.equals(s))) r.add(new Fila(x.id, x.misionId, x.ciclo, x.progreso, x.estado, x.nivel, x.veces)); return r; }
        public Set<LocalDate> diasActivos(long u, LocalDate desde) { f(); Set<LocalDate> r = new HashSet<>(); for (LocalDate d : dias.getOrDefault(u, Set.of())) if (!d.isBefore(desde)) r.add(d); return r; }
        public void marcarDia(long u, LocalDate d) { f(); dias.computeIfAbsent(u, k -> new HashSet<>()).add(d); }
        public Fila filaParaActualizar(long u, long m, String c, LocalDateTime a) { f(); for (FilaM x : filas) if (x.userId == u && x.misionId == m && x.ciclo.equals(c)) return new Fila(x.id, m, c, x.progreso, x.estado, x.nivel, x.veces); FilaM n = new FilaM(); n.id = ++seq; n.userId = u; n.misionId = m; n.ciclo = c; filas.add(n); return new Fila(n.id, m, c, 0, "EN_CURSO", 0, 0); }
        FilaM porId(long id) { return filas.stream().filter(x -> x.id == id).findFirst().get(); }
        public boolean contarTitulo(long fid, long t) { f(); return titulos.add(fid + ":" + t); }
        public void actualizarProgreso(long fid, int p, LocalDateTime a) { f(); FilaM x = porId(fid); if (x.estado.equals("EN_CURSO")) x.progreso = p; }
        public boolean cumplir(long fid, int p, LocalDateTime a) { f(); FilaM x = porId(fid); if (!x.estado.equals("EN_CURSO")) return false; x.progreso = p; x.estado = "CUMPLIDA"; x.cumplidaAt = a; return true; }
        public void otorgarSello(long u, long s, int nivel, long m, LocalDateTime a) { f(); Map<Long, int[]> mp = sellos.computeIfAbsent(u, k -> new HashMap<>()); int[] cur = mp.get(s); if (cur == null) { mp.put(s, new int[]{nivel}); sellosFecha.computeIfAbsent(u, k -> new HashMap<>()).put(s, a); } else if (nivel > cur[0]) { cur[0] = nivel; sellosFecha.get(u).put(s, a); } }
        public long crearCelebracion(long u, long m, int nivel, String tipo, LocalDateTime a) { f(); Cel c = new Cel(); c.id = ++seqCel; c.userId = u; c.misionId = m; c.nivel = nivel; c.tipo = tipo; c.at = a; cels.add(c); return c.id; }
        public void actualizarContador(long fid, int p, int nivel, int veces, LocalDateTime a) { f(); FilaM x = porId(fid); x.progreso = p; x.nivel = nivel; x.veces = veces; }
        public List<Cumplida> cumplidasSinVer(long u) { f(); List<Cumplida> r = new ArrayList<>(); for (Cel c : cels) if (c.userId == u && c.visto == null) r.add(new Cumplida(c.id, c.misionId, c.nivel, c.tipo, c.at)); r.sort(Comparator.comparing(Cumplida::cumplidaAt).thenComparingLong(Cumplida::id)); return r; }
        public boolean marcarVisto(long u, long id, LocalDateTime a) { f(); for (Cel c : cels) if (c.id == id && c.userId == u && c.visto == null) { c.visto = a; return true; } return false; }
        public List<SelloGanado> sellosGanados(long u, int lim) { f(); List<SelloGanado> r = new ArrayList<>(); for (var e : sellos.getOrDefault(u, Map.of()).entrySet()) { Sello s = misiones.stream().map(Mision::sello).filter(x -> x.id() == e.getKey()).findFirst().get(); r.add(new SelloGanado(s.id(), s.nombre(), s.categoria(), s.descripcion(), e.getValue()[0], sellosFecha.get(u).get(s.id()), s.imagenUrl())); } r.sort(Comparator.comparing(SelloGanado::ganadoAt).reversed()); return r.size() > lim ? r.subList(0, lim) : r; }
        public int porDescubrir(long u) { f(); Set<Long> r = new HashSet<>(); for (Mision m : misiones) if (m.visibilidad().equals("MISTERIOSA") && (sellos.getOrDefault(u, Map.of()).get(m.sello().id()) == null || sellos.get(u).get(m.sello().id())[0] < m.nivelSello())) r.add(m.sello().id()); return r.size(); }
    }
    static class Catalogo implements CatalogoPeliculas {
        Map<Long, Set<Long>> g = new HashMap<>(), d = new HashMap<>(), el = new HashMap<>(), sagaPartes = new HashMap<>(); boolean caido = false; int llamadas = 0;
        public Set<Long> generos(long id) { llamadas++; return caido ? Set.of() : g.getOrDefault(id, Set.of()); }
        public Set<Long> directores(long id) { llamadas++; return caido ? Set.of() : d.getOrDefault(id, Set.of()); }
        int ultimasCandidatas = -1;
        public Set<Long> sagas(long id, Collection<Long> cand) { llamadas++; ultimasCandidatas = cand.size(); Set<Long> r = new HashSet<>(); if (!caido) for (Long s : cand) if (sagaPartes.getOrDefault(s, Set.of()).contains(id)) r.add(s); return r; }
        public Set<Long> elenco(long id) { llamadas++; return caido ? Set.of() : el.getOrDefault(id, Set.of()); }
    }

    static long sid = 0;
    static Sello S(String n, String c) { return new Sello(++sid, n, c, "Sello " + n); }
    static Mision M(long id, String t, String metrica, int meta, String vig, String tipo, Long gen, Long dir, boolean dist, Sello s, int nivel, double esf, Long prereq, Integer nec, String visib, LocalDateTime desde, LocalDateTime hasta, int orden) {
        return new Mision(id, t, t + ".", "Cumpliste " + t + ".", s.categoria(), metrica, meta, "votos", vig, tipo, gen, dir, dist, s, nivel, esf, prereq, nec, visib, desde, hasta, null, null, orden);
    }
    static final LocalDate D0 = LocalDate.of(2026, 11, 10);   // martes
    static LocalDateTime t(int dia, int h) { return D0.plusDays(dia).atTime(h, 0); }

    static Memoria base; static Catalogo cat; static MisionService svc;
    static void montar() {
        base = new Memoria(); cat = new Catalogo(); svc = new MisionService(base, cat);
        Sello s1 = S("Tres al hilo", "votos"), s2 = S("Voz del día", "comentarios"), s3 = S("Constante", "constancia"), s4 = S("Terror", "genero"), s5 = S("Autor", "autor"),
              s6 = S("Suspenso", "especial"), s7 = S("Fan de series", "votos"), s8 = S("Crítico", "votos"), s9 = S("Sorpresa", "especial"), s10 = S("Votos", "votos"), s11 = S("Trivia", "trivia");
        base.misiones.addAll(List.of(
            M(1, "Tres al hilo", "VOTOS", 3, "HOY", null, null, null, false, s1, 1, 1, null, null, "VISIBLE", null, null, 20),
            M(2, "Voz del día", "COMENTARIOS", 2, "HOY", null, null, null, false, s2, 1, 2, null, null, "VISIBLE", null, null, 30),
            M(3, "Constante", "RACHA_DIAS", 7, "RACHA", null, null, null, false, s3, 1, 3, null, null, "VISIBLE", null, null, 40),
            M(4, "Maratón de terror", "VOTOS", 10, "PERMANENTE", "PELICULA", 27L, null, false, s4, 1, 1, null, null, "VISIBLE", null, null, 60),
            M(5, "Cine de autor", "COMENTARIOS", 5, "PERMANENTE", "PELICULA", null, 525L, true, s5, 1, 2, null, null, "VISIBLE", null, null, 70),
            M(6, "Semana del suspenso", "VOTOS", 5, "EVENTO", "PELICULA", 53L, null, false, s6, 1, 1, null, null, "VISIBLE", t(6, 0), t(12, 23), 100),
            M(7, "Fan de las series", "VOTOS", 5, "SEMANA", "SERIE", null, null, false, s7, 1, 1, null, null, "VISIBLE", null, null, 80),
            M(8, "Crítico en serie", "VOTOS", 25, "PERMANENTE", "SERIE", null, null, false, s8, 1, 1, 7L, null, "VISIBLE", null, null, 90),
            M(9, "Misión sorpresa", "VOTOS", 2, "PERMANENTE", null, null, null, false, s9, 1, 1, null, 3, "MISTERIOSA", null, null, 200),
            M(10, "Primer voto", "VOTOS", 1, "PERMANENTE", null, null, null, false, s10, 1, 1, null, null, "VISIBLE", null, null, 10),
            M(11, "Voto x3", "VOTOS", 3, "PERMANENTE", null, null, null, false, s10, 2, 1, 10L, null, "VISIBLE", null, null, 11),
            M(12, "Mente de trivia", "TRIVIAS", 2, "PERMANENTE", null, null, null, false, s11, 1, 1, null, null, "VISIBLE", null, null, 110)));
        for (long i = 1000; i < 1030; i++) cat.g.put(i, Set.of(27L));                       // películas de terror
        for (long i = 2000; i < 2010; i++) cat.g.put(i, Set.of(35L));                       // comedias
        for (long i = 3000; i < 3010; i++) cat.g.put(i, Set.of(53L));                       // suspenso
        for (long i = 4000; i < 4010; i++) cat.d.put(i, Set.of(525L));                      // Nolan
        cat.d.put(5000L, Set.of(1L));
    }
    static void voto(long u, long peli, LocalDateTime a) { svc.procesar(ActividadEvento.votoPelicula(u, peli), a); }
    static void comentario(long u, long peli, LocalDateTime a) { svc.procesar(ActividadEvento.comentarioPelicula(u, peli), a); }
    static void votoSerie(long u, long serie, LocalDateTime a) { svc.procesar(ActividadEvento.votoSerie(u, serie), a); }
    @SuppressWarnings("unchecked") static Map<String, Object> mapa(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Map<String, Object>> lista(Object o) { return (List<Map<String, Object>>) o; }
    static String titulo(Map<String, Object> r, String k) { return r.containsKey(k) ? (String) mapa(r.get(k)).get("titulo") : null; }
    static int progresoDe(long u, long misionId, LocalDateTime a) { for (var m : lista(svc.completo(u, a).get("enCurso"))) if (((Number) m.get("id")).longValue() == misionId) return (Integer) m.get("progreso"); return -1; }
    static List<String> titulos(Object l) { return lista(l).stream().map(m -> (String) m.get("titulo")).toList(); }


    // ======================================================================================== métricas y filtros nuevos
    static Mision Mx(long id, String t, String metrica, int meta, String tipo, Long gen, Long saga, Long actor, boolean dist, Sello s, double esf, int orden) {
        return new Mision(id, t, t + ".", "Cumpliste " + t + ".", s.categoria(), metrica, meta, "x", "PERMANENTE", tipo, gen, null, saga, actor, dist, s, 1, esf, null, null, "VISIBLE", null, null, null, null, orden);
    }
    static void nuevas() {
        System.out.println("=== 13) Sagas y actores/actrices (filtros nuevos)");
        base = new Memoria(); cat = new Catalogo(); svc = new MisionService(base, cat);
        base.misiones.addAll(List.of(
            Mx(1, "Maratón Harry Potter", "VOTOS", 3, "PELICULA", null, 1241L, null, false, S("HP", "saga"), 1, 1),
            Mx(2, "Fan de Meryl Streep", "VOTOS", 3, "PELICULA", null, null, 5064L, false, S("Meryl", "elenco"), 1, 2),
            Mx(3, "Conversador", "INTERVENCIONES", 4, null, null, null, null, false, S("Conversador", "comunidad"), 1.5, 3),
            Mx(4, "En muchas conversaciones", "INTERVENCIONES", 3, null, null, null, null, true, S("Muchas", "comunidad"), 1.5, 4),
            Mx(5, "Primer canje", "CANJES", 1, null, null, null, null, false, S("Canje", "premios"), 3, 5),
            Mx(6, "Coleccionista de premios", "CANJES", 3, null, null, null, null, true, S("Colecc", "premios"), 3, 6),
            Mx(7, "Con suerte", "SORTEOS", 2, null, null, null, null, false, S("Suerte", "premios"), 2, 7),
            Mx(8, "Gustos de películas", "GUSTOS", 4, "PELICULA", null, null, null, false, S("GustosP", "perfil"), 2, 8),
            Mx(9, "Gustos de series", "GUSTOS", 4, "SERIE", null, null, null, false, S("GustosS", "perfil"), 2, 9),
            Mx(10, "Perfil completo", "GUSTOS", 8, null, null, null, null, false, S("Perfil", "perfil"), 2, 10),
            Mx(11, "Cinéfilo generoso", "RECOMENDACIONES", 3, "PELICULA", null, null, null, true, S("Generoso", "recomendaciones"), 2, 11),
            Mx(12, "Recomendá una serie", "RECOMENDACIONES", 1, "SERIE", null, null, null, false, S("RecSerie", "recomendaciones"), 2, 12),
            Mx(13, "Recomendador de terror", "RECOMENDACIONES", 2, "PELICULA", 27L, null, null, false, S("RecTerror", "recomendaciones"), 2, 13)));
        for (long i = 6000; i < 6004; i++) cat.sagaPartes.computeIfAbsent(1241L, k -> new HashSet<>()).add(i);
        cat.el.put(8000L, Set.of(5064L, 1L)); cat.el.put(8001L, Set.of(5064L)); cat.el.put(8002L, Set.of(99L)); cat.el.put(8003L, Set.of(5064L));
        for (long i = 1000; i < 1030; i++) cat.g.put(i, Set.of(27L));
        voto(1, 6000, t(0, 9)); voto(1, 6001, t(0, 10)); voto(1, 7000, t(0, 11));
        chk(progresoDe(1, 1, t(0, 12)) == 2, "saga: 2 películas de la saga + 1 que no es de la saga = 2 de 3 (la ajena no cuenta)");
        voto(1, 6002, t(0, 13));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("HP")), "la 3.ª película de la saga otorga el sello");
        voto(1, 8000, t(0, 14)); voto(1, 8001, t(0, 15)); voto(1, 8002, t(0, 16));
        chk(progresoDe(1, 2, t(0, 17)) == 2, "elenco: 2 películas con la actriz en el reparto + 1 sin ella = 2 de 3");
        voto(1, 8003, t(0, 18));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Meryl")), "la 3.ª película con la actriz otorga el sello");
        voto(2, 6000, t(0, 9)); cat.caido = true; voto(2, 6001, t(0, 10)); cat.caido = false;
        chk(progresoDe(2, 1, t(0, 11)) == 1, "si TMDB no responde, esa película no cuenta para la saga y no se rompe nada (1 de 3)");
        voto(3, 5555, t(0, 9)); chk(progresoDe(3, 1, t(0, 10)) == 0 && progresoDe(3, 2, t(0, 10)) == 0, "una película que no es de la saga ni tiene a la actriz no mueve ninguna");
        Mision vieja = new Mision(1, "V", "v", null, "votos", "VOTOS", 3, "votos", "HOY", null, null, null, false, S("Vieja", "votos"), 1, 1, null, null, "VISIBLE", null, null, null, null, 1);
        chk(vieja.sagaId() == null && vieja.actorId() == null && !vieja.necesitaTmdb(), "el constructor de compatibilidad crea misiones sin saga ni actor (lo anterior sigue funcionando)");
        chk(MisionLogica.coincide(Mx(1, "S", "VOTOS", 3, "PELICULA", null, 1241L, null, false, S("S", "saga"), 1, 1), ActividadEvento.votoPelicula(1, 6000L), Set.of(), Set.of(), Set.of(1241L), Set.of()) && !MisionLogica.coincide(Mx(1, "S", "VOTOS", 3, "PELICULA", null, 1241L, null, false, S("S", "saga"), 1, 1), ActividadEvento.votoPelicula(1, 6000L), Set.of(), Set.of(), Set.of(), Set.of()), "coincide() exige que la película pertenezca a la saga");

        System.out.println("=== 14) Intervenciones en publicaciones");
        cat.llamadas = 0;
        for (long pub : new long[]{1, 1, 2, 3}) svc.procesar(ActividadEvento.intervencion(5, pub), t(1, 10));
        chk(progresoDe(5, 3, t(1, 11)) == -1 && base.sellosGanados(5, 99).stream().anyMatch(s -> s.nombre().equals("Conversador")), "4 intervenciones (aunque dos sean en la misma publicación) cumplen «Conversador»");
        chk(base.sellosGanados(5, 99).stream().anyMatch(s -> s.nombre().equals("Muchas")), "3 publicaciones DISTINTAS cumplen «En muchas conversaciones» (la repetida no cuenta doble)");
        svc.procesar(ActividadEvento.intervencion(6, 1L), t(1, 10)); svc.procesar(ActividadEvento.intervencion(6, 1L), t(1, 11));
        chk(progresoDe(6, 4, t(1, 12)) == 1 && progresoDe(6, 3, t(1, 12)) == 2, "dos intervenciones en la misma publicación: 2 para la común y 1 para la de publicaciones distintas");
        chk(cat.llamadas == 0, "una intervención nunca consulta a TMDB (su targetId es una publicación, no una película)");

        System.out.println("=== 15) Premios canjeados y sorteos");
        cat.llamadas = 0;
        svc.procesar(ActividadEvento.canje(7, 10L), t(1, 10));
        chk(base.sellosGanados(7, 99).stream().anyMatch(s -> s.nombre().equals("Canje")), "el primer canje otorga «Primer canje»");
        svc.procesar(ActividadEvento.canje(7, 10L), t(1, 11)); svc.procesar(ActividadEvento.canje(7, -10L), t(1, 12));
        chk(progresoDe(7, 6, t(1, 13)) == 2, "el mismo premio dos veces + un premio Premium con el mismo número (id negativo) = 2 premios distintos");
        svc.procesar(ActividadEvento.canje(7, 11L), t(1, 14));
        chk(base.sellosGanados(7, 99).stream().anyMatch(s -> s.nombre().equals("Colecc")), "el 3.er premio distinto otorga «Coleccionista de premios»");
        svc.procesar(ActividadEvento.sorteo(7, 20L), t(1, 15)); chk(progresoDe(7, 7, t(1, 16)) == 1, "una participación en un sorteo = 1 de 2");
        svc.procesar(ActividadEvento.sorteo(7, 21L), t(1, 17)); chk(base.sellosGanados(7, 99).stream().anyMatch(s -> s.nombre().equals("Suerte")), "la 2.ª otorga «Con suerte»");
        chk(cat.llamadas == 0, "ni canjes ni sorteos consultan a TMDB");

        System.out.println("=== 16) Mis gustos (8 espacios)");
        cat.llamadas = 0;
        svc.procesar(ActividadEvento.gusto(8, 1, false), t(2, 9)); svc.procesar(ActividadEvento.gusto(8, 1, false), t(2, 10));
        chk(progresoDe(8, 8, t(2, 11)) == 1, "guardar dos veces el MISMO espacio cuenta una sola vez (1 de 4)");
        for (int e = 2; e <= 4; e++) svc.procesar(ActividadEvento.gusto(8, e, false), t(2, 11 + e));
        chk(base.sellosGanados(8, 99).stream().anyMatch(s -> s.nombre().equals("GustosP")) && base.sellosGanados(8, 99).stream().noneMatch(s -> s.nombre().equals("GustosS")), "los 4 gustos de películas otorgan solo «Gustos de películas»");
        chk(progresoDe(8, 10, t(2, 20)) == 4, "y «Perfil completo» va 4 de 8");
        for (int e = 5; e <= 8; e++) svc.procesar(ActividadEvento.gusto(8, e, true), t(3, 8 + e));
        chk(base.sellosGanados(8, 99).stream().anyMatch(s -> s.nombre().equals("GustosS")) && base.sellosGanados(8, 99).stream().anyMatch(s -> s.nombre().equals("Perfil")), "los 4 de series otorgan «Gustos de series» y los 8 juntos «Perfil completo»");
        chk(cat.llamadas == 0, "guardar un gusto de película NO consulta a TMDB aunque su contenido sea PELICULA (el targetId es el espacio, no una película)");

        System.out.println("=== 17) Recomendar películas y series");
        cat.llamadas = 0;
        svc.procesar(ActividadEvento.recomendacionPelicula(9, 1000L), t(2, 9)); svc.procesar(ActividadEvento.recomendacionPelicula(9, 1000L), t(2, 10));
        chk(progresoDe(9, 11, t(2, 11)) == 1, "recomendar la MISMA película dos veces cuenta una sola vez en la misión de títulos distintos (1 de 3)");
        chk(base.sellosGanados(9, 99).stream().anyMatch(s -> s.nombre().equals("RecTerror")), "2 recomendaciones de terror otorgan «Recomendador de terror»");
        svc.procesar(ActividadEvento.recomendacionSerie(9, 55L), t(2, 12));
        chk(base.sellosGanados(9, 99).stream().anyMatch(s -> s.nombre().equals("RecSerie")), "recomendar una serie otorga «Recomendá una serie»");
        chk(progresoDe(9, 11, t(2, 13)) == 1, "y la recomendación de una serie no suma a la de películas");
        svc.procesar(ActividadEvento.recomendacionPelicula(9, 2000L), t(2, 14)); svc.procesar(ActividadEvento.recomendacionPelicula(9, 2001L), t(2, 15));
        chk(base.sellosGanados(9, 99).stream().anyMatch(s -> s.nombre().equals("Generoso")), "3 películas distintas otorgan «Cinéfilo generoso»");
        chk(cat.llamadas > 0, "las recomendaciones de películas SÍ consultan a TMDB (para el género)");
    }

    // ======================================================================================== escaleras y repetición
    static Mision Me(long id, String t, String metrica, int meta, String vigencia, int[] escalera, Integer paso, String sobre, Sello s, double esf, int orden) {
        return new Mision(id, t, t + ".", "Cumpliste " + t + ".", s.categoria(), metrica, meta, "x", vigencia, null, null, null, null, null, false, s, 1, esf, null, null, "VISIBLE", null, null, null, null, orden, escalera, paso, sobre);
    }
    static long celsM(long u, long misionId, String tipo) { return base.cels.stream().filter(c -> c.userId == u && c.misionId == misionId && c.tipo.equals(tipo)).count(); }
    static long cels(long u, String tipo) { return base.cels.stream().filter(c -> c.userId == u && c.tipo.equals(tipo)).count(); }
    static int nivelSello(long u, String nombre) { return base.sellosGanados(u, 99).stream().filter(s -> s.nombre().equals(nombre)).mapToInt(SelloGanado::nivel).findFirst().orElse(0); }
    static Map<String, Object> enCursoDe(long u, String titulo, LocalDateTime a) { return lista(svc.completo(u, a).get("enCurso")).stream().filter(m -> m.get("titulo").equals(titulo)).findFirst().orElse(null); }

    static void escaleras() {
        System.out.println("=== 18) Escalera progresiva: una misión que se rearma, con paso fijo");
        base = new Memoria(); cat = new Catalogo(); svc = new MisionService(base, cat);
        base.misiones.add(Me(1, "Votador", "VOTOS", 1, "PERMANENTE", new int[]{1, 3, 6}, 3, "PROGRESO", S("Votador", "votos"), 1, 1));
        voto(1, 1, t(0, 9));
        chk(nivelSello(1, "Votador") == 1 && cels(1, "NIVEL") == 1, "el 1.er voto cumple el 1.er escalón: el sello queda en Nv 1 y hay una celebración");
        Map<String, Object> m = enCursoDe(1, "Votador", t(0, 10));
        chk(m != null && (Integer) m.get("meta") == 3 && (Integer) m.get("progreso") == 1 && (Integer) m.get("nivel") == 2 && Boolean.TRUE.equals(m.get("escalera")), "la misión sigue en curso y ahora pide 3 (va 1 de 3, nivel 2): se rearmó sola");
        voto(1, 2, t(0, 10)); voto(1, 3, t(0, 11));
        chk(nivelSello(1, "Votador") == 2 && (Integer) enCursoDe(1, "Votador", t(0, 12)).get("meta") == 6 && (Integer) enCursoDe(1, "Votador", t(0, 12)).get("progreso") == 3, "con 3 votos sube a Nv 2 y pide 6: el conteo es ACUMULADO (va 3 de 6, no se reinició)");
        for (int i = 4; i <= 6; i++) voto(1, i, t(0, 12));
        chk(nivelSello(1, "Votador") == 3 && (Integer) enCursoDe(1, "Votador", t(0, 13)).get("meta") == 9, "con 6 votos Nv 3; el escalón siguiente ya es del paso fijo: 6 + 3 = 9");
        for (int i = 7; i <= 15; i++) voto(1, i, t(0, 13));
        chk(nivelSello(1, "Votador") == 6 && (Integer) enCursoDe(1, "Votador", t(0, 14)).get("meta") == 18, "el paso fijo sigue solo: 9 → Nv 4, 12 → Nv 5, 15 → Nv 6; la meta siguiente es 18");
        chk(celsM(1, 1, "NIVEL") == 6 && celsM(1, 1, "LIVIANA") == 0, "una celebración por cada nivel (6) y ningún cartelito liviano");

        System.out.println("=== 19) Escalera acotada: termina en el último escalón");
        svc.procesar(ActividadEvento.publicacion(2), t(1, 9));
        base.misiones.add(Me(2, "Publicador", "PUBLICACIONES", 1, "PERMANENTE", new int[]{1, 2}, null, "PROGRESO", S("Publicador", "comunidad"), 2, 2));
        svc.procesar(ActividadEvento.publicacion(3), t(1, 9)); svc.procesar(ActividadEvento.publicacion(3), t(1, 10));
        chk(nivelSello(3, "Publicador") == 2 && enCursoDe(3, "Publicador", t(1, 11)) == null, "sin paso fijo, el último escalón (2) la termina: Nv 2 y la misión deja de ofrecerse");
        svc.procesar(ActividadEvento.publicacion(3), t(1, 12));
        chk(nivelSello(3, "Publicador") == 2 && celsM(3, 2, "NIVEL") == 2, "las publicaciones siguientes no hacen nada más (no hay un 3.er nivel ni celebración extra)");

        System.out.println("=== 20) Escalera de racha: el nivel se conserva aunque la racha se corte");
        base.misiones.add(Me(3, "Racha", "RACHA_DIAS", 2, "RACHA", new int[]{2, 4}, null, "PROGRESO", S("Racha", "constancia"), 3, 3));
        voto(4, 1, t(0, 9)); voto(4, 2, t(1, 9));
        chk(nivelSello(4, "Racha") == 1, "2 días seguidos cumplen el 1.er escalón (Nv 1)");
        voto(4, 3, t(3, 9));
        chk(nivelSello(4, "Racha") == 1 && (Integer) enCursoDe(4, "Racha", t(3, 10)).get("progreso") == 1 && (Integer) enCursoDe(4, "Racha", t(3, 10)).get("meta") == 4, "se cortó la racha: el progreso vuelve a 1 pero el Nv 1 se conserva y la meta sigue en 4");
        voto(4, 4, t(4, 9)); voto(4, 5, t(5, 9)); voto(4, 6, t(6, 9));
        chk(nivelSello(4, "Racha") == 2 && enCursoDe(4, "Racha", t(6, 10)) == null, "4 días seguidos después: Nv 2 y la escalera termina");

        System.out.println("=== 21) Repetición: la misión del día sube de nivel por veces cumplidas");
        base.misiones.add(Me(4, "Del día", "VOTOS", 2, "HOY", new int[]{1, 3}, null, "VECES", S("DelDia", "votos"), 1, 4));
        voto(5, 1, t(0, 9)); chk(enCursoDe(5, "Del día", t(0, 10)) != null && (Integer) enCursoDe(5, "Del día", t(0, 10)).get("progreso") == 1, "día 1: 1 de 2 votos");
        voto(5, 2, t(0, 10));
        chk(nivelSello(5, "DelDia") == 1 && celsM(5, 4, "NIVEL") == 1 && celsM(5, 4, "LIVIANA") == 0, "día 1: al cumplir el día es la 1.ª vez: sube al Nv 1 con celebración");
        chk(enCursoDe(5, "Del día", t(0, 11)) == null, "y por hoy ya no se ofrece (queda «cumplida hoy»)");
        voto(5, 3, t(0, 12)); voto(5, 4, t(0, 13));
        chk(base.filas.stream().filter(f -> f.userId == 5 && f.misionId == 4 && f.ciclo.equals("P")).findFirst().get().veces == 1, "más votos el mismo día NO la cuentan otra vez (sigue en 1 vez)");
        Map<String, Object> m2 = enCursoDe(5, "Del día", t(1, 9));
        chk(m2 != null && (Integer) m2.get("progreso") == 0 && (Integer) m2.get("veces") == 1 && (Integer) m2.get("metaVeces") == 3, "día 2: reaparece desde 0, con «1 vez cumplida» y el próximo nivel en 3 veces");
        voto(5, 5, t(1, 9)); voto(5, 6, t(1, 10));
        chk(nivelSello(5, "DelDia") == 1 && celsM(5, 4, "LIVIANA") == 1 && celsM(5, 4, "NIVEL") == 1, "día 2: es la 2.ª vez y NO completa un escalón: solo un cartelito liviano, sin celebración");
        Map<String, Object> rs = svc.resumen(5, t(1, 11)); List<Map<String, Object>> avisos = lista(rs.get("avisos"));
        chk(avisos.size() == 1 && avisos.get(0).get("texto").equals("Cumplida hoy") && (Integer) avisos.get(0).get("veces") == 2 && lista(rs.get("cumplidasSinVer")).stream().filter(x -> x.get("titulo").equals("Del día")).count() == 1, "el resumen trae el aviso «Cumplida hoy» (2 veces) aparte de la celebración pendiente del día 1");
        svc.visto(5, ((Number) avisos.get(0).get("id")).longValue());
        chk(!svc.resumen(5, t(1, 12)).containsKey("avisos"), "una vez visto, el aviso no vuelve");
        voto(5, 7, t(2, 9)); voto(5, 8, t(2, 10));
        chk(nivelSello(5, "DelDia") == 2 && celsM(5, 4, "NIVEL") == 2 && celsM(5, 4, "LIVIANA") == 1, "día 3: es la 3.ª vez → sube al Nv 2 con celebración");
        chk(enCursoDe(5, "Del día", t(3, 9)) == null, "con la escalera terminada (sin paso fijo) la misión del día deja de ofrecerse");

        System.out.println("=== 22) Repetición semanal");
        base.misiones.add(Me(5, "De la semana", "VOTOS", 2, "SEMANA", new int[]{1, 2}, null, "VECES", S("Semanal", "votos"), 1, 5));
        voto(6, 1, t(0, 9)); voto(6, 2, t(1, 9));
        chk(nivelSello(6, "Semanal") == 1 && enCursoDe(6, "De la semana", t(2, 9)) == null, "semana 1: 2 votos en la semana la cumplen (Nv 1) y no se ofrece más esa semana");
        chk(enCursoDe(6, "De la semana", t(7, 9)) != null, "la semana siguiente vuelve a ofrecerse");
        voto(6, 3, t(7, 9)); voto(6, 4, t(8, 9));
        chk(nivelSello(6, "Semanal") == 2, "semana 2: 2.ª vez → Nv 2");

        System.out.println("=== 23) Las misiones comunes no cambian");
        base = new Memoria(); cat = new Catalogo(); svc = new MisionService(base, cat);
        base.misiones.add(M(1, "Común", "VOTOS", 2, "PERMANENTE", null, null, null, false, S("Común", "votos"), 1, 1, null, null, "VISIBLE", null, null, 1));
        voto(7, 1, t(0, 9)); voto(7, 2, t(0, 10));
        chk(nivelSello(7, "Común") == 1 && cels(7, "NIVEL") == 1 && enCursoDe(7, "Común", t(0, 11)) == null, "una misión sin escalera se cumple una vez, deja su celebración y queda ganada");
        chk(!svc.resumen(7, t(0, 11)).containsKey("avisos") || lista(svc.resumen(7, t(0, 11)).get("avisos")).isEmpty(), "y no genera cartelitos livianos");
    }

    // ======================================================================================== rotación personal y descarte
    static Mision Mr(long id, String t, String familia, Long genero, Long saga, Long actor, Long director, int meta, Sello s, int orden) {
        String cat = switch (familia) { case "SAGA" -> "saga"; case "ACTOR" -> "elenco"; case "DIRECTOR" -> "autor"; default -> "genero"; };
        return new Mision(id, t, t + ".", "Cumpliste " + t + ".", cat, "VOTOS", meta, "votos", "PERMANENTE", "PELICULA", genero, director, saga, actor, false, s, 1, 1.0, null, null, "VISIBLE", null, null, null, null, orden, null, null, "PROGRESO", familia);
    }
    static void objetivo(long misionId, String familia, String nombre) { Memoria.Obj o = new Memoria.Obj(); o.id = misionId; o.misionId = misionId; o.familia = familia; o.nombre = nombre; base.objetivos.add(o); }
    static String nombreMision(long id) { return base.misiones.stream().filter(m -> m.id() == id).findFirst().get().titulo(); }
    static Memoria.AsigM asig(long u, String familia, String estado) { return base.asigs.stream().filter(a -> a.userId == u && a.familia.equals(familia) && a.estado.equals(estado)).findFirst().orElse(null); }
    static List<Map<String, Object>> rotativas(long u, LocalDateTime a) { return lista(svc.completo(u, a).get("enCurso")).stream().filter(m -> m.get("familia") != null).toList(); }
    static void montarRotacion() {
        base = new Memoria(); cat = new Catalogo(); svc = new MisionService(base, cat); svc.usarAzar(new Random(42));
        base.misiones.addAll(List.of(
            Mr(101, "Saga Harry Potter", "SAGA", null, 1241L, null, null, 3, S("SagaHP", "saga"), 1), Mr(102, "Saga Señor de los Anillos", "SAGA", null, 119L, null, null, 2, S("SagaLOTR", "saga"), 2), Mr(103, "Saga Matrix", "SAGA", null, 2344L, null, null, 2, S("SagaMatrix", "saga"), 3),
            Mr(111, "Fan de Meryl Streep", "ACTOR", null, null, 5064L, null, 2, S("FanMeryl", "elenco"), 4), Mr(112, "Fan de Tom Hanks", "ACTOR", null, null, 31L, null, 2, S("FanTom", "elenco"), 5),
            Mr(121, "Cine de Christopher Nolan", "DIRECTOR", null, null, null, 525L, 2, S("CineNolan", "autor"), 6),
            Mr(131, "Maratón de terror", "GENERO", 27L, null, null, null, 2, S("MaratonTerror", "genero"), 7), Mr(132, "Maratón de comedia", "GENERO", 35L, null, null, null, 2, S("MaratonComedia", "genero"), 8)));
        objetivo(101, "SAGA", "Harry Potter"); objetivo(102, "SAGA", "El Señor de los Anillos"); objetivo(103, "SAGA", "Matrix");
        objetivo(111, "ACTOR", "Meryl Streep"); objetivo(112, "ACTOR", "Tom Hanks"); objetivo(121, "DIRECTOR", "Christopher Nolan");
        objetivo(131, "GENERO", "Terror"); objetivo(132, "GENERO", "Comedia");
        for (long i = 6000; i < 6010; i++) cat.sagaPartes.computeIfAbsent(1241L, k -> new HashSet<>()).add(i);
        for (long i = 7000; i < 7010; i++) cat.sagaPartes.computeIfAbsent(119L, k -> new HashSet<>()).add(i);
        for (long i = 7100; i < 7110; i++) cat.sagaPartes.computeIfAbsent(2344L, k -> new HashSet<>()).add(i);
        for (long i = 8000; i < 8010; i++) cat.el.put(i, Set.of(5064L)); for (long i = 8100; i < 8110; i++) cat.el.put(i, Set.of(31L));
        for (long i = 4000; i < 4010; i++) cat.d.put(i, Set.of(525L)); for (long i = 1000; i < 1030; i++) cat.g.put(i, Set.of(27L)); for (long i = 2000; i < 2010; i++) cat.g.put(i, Set.of(35L));
    }

    static void rotacion() {
        System.out.println("=== 24) Cada usuario recibe SU objetivo actual y el siguiente en cada familia");
        montarRotacion();
        List<Map<String, Object>> rot = rotativas(1, t(0, 9));
        chk(rot.size() == 4 && rot.stream().map(m -> (String) m.get("familia")).collect(java.util.stream.Collectors.toSet()).equals(Set.of("SAGA", "ACTOR", "DIRECTOR", "GENERO")), "el usuario ve UNA misión rotativa por familia (saga, actor/actriz, director y género): " + rot.stream().map(m -> m.get("titulo")).toList());
        chk(rot.stream().allMatch(m -> Boolean.TRUE.equals(m.get("descartable"))), "todas llevan la ✕ (descartable)");
        List<Map<String, Object>> sig = lista(svc.completo(1, t(0, 9)).get("proximas")).stream().filter(m -> "SIGUIENTE".equals(m.get("estado"))).toList();
        chk(sig.size() == 3 && sig.stream().allMatch(m -> ((String) m.get("desbloqueo")).contains("próxima misión")), "y en «Próximas» ve su siguiente ya sorteada (3: el director no tiene otra para sortear): " + sig.stream().map(m -> m.get("titulo")).toList());
        chk(base.asigs.stream().filter(a -> a.userId == 1).count() == 7, "en total 7 objetivos asignados (4 actuales + 3 siguientes); las demás misiones del catálogo no se le muestran");
        Set<String> visibles = new HashSet<>(); lista(svc.completo(1, t(0, 9)).get("enCurso")).forEach(m -> visibles.add((String) m.get("titulo"))); lista(svc.completo(1, t(0, 9)).get("proximas")).forEach(m -> visibles.add((String) m.get("titulo")));
        chk(visibles.size() == 7, "ve solo esas 7 misiones, no las 8 del catálogo");
        svc.completo(2, t(0, 9));
        chk(base.asigs.stream().filter(a -> a.userId == 2).count() == 7 && base.asigs.stream().filter(a -> a.userId == 1).count() == 7, "otro usuario recibe las suyas, independientes (cada uno con sus 7)");
        long antes = base.asigs.size(); svc.completo(1, t(0, 10)); svc.resumen(1, t(0, 10));
        chk(base.asigs.size() == antes, "mirar de nuevo no cambia nada ni sortea otra vez (es estable)");

        System.out.println("=== 25) Solo cuenta la misión ACTUAL (la siguiente no)");
        long idSaga = asig(1, "SAGA", "ACTUAL").misionId, idSagaSig = asig(1, "SAGA", "SIGUIENTE").misionId;
        Map<Long, Long> sagaDe = Map.of(101L, 1241L, 102L, 119L, 103L, 2344L); Map<Long, Long> base0 = Map.of(1241L, 6000L, 119L, 7000L, 2344L, 7100L);
        long pelActual = base0.get(sagaDe.get(idSaga)), pelSig = base0.get(sagaDe.get(idSagaSig));
        voto(1, pelSig, t(0, 11)); voto(1, pelSig + 1, t(0, 12));
        chk(base.filas.stream().noneMatch(f -> f.userId == 1 && f.misionId == idSagaSig), "votar películas de la saga SIGUIENTE no le suma nada todavía");
        voto(1, pelActual, t(0, 13));
        chk(base.filas.stream().anyMatch(f -> f.userId == 1 && f.misionId == idSaga && f.progreso == 1), "votar una película de la saga ACTUAL sí suma (1)");

        System.out.println("=== 26) Al completarla, pasa la siguiente (desde cero) y se sortea otra");
        int meta = idSaga == 101 ? 3 : 2;
        for (int i = 1; i < meta; i++) voto(1, pelActual + i, t(0, 14 + i));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().startsWith("Saga")) && base.cels.stream().anyMatch(c -> c.userId == 1 && c.misionId == idSaga && c.tipo.equals("NIVEL")), "la saga se completa: sello y celebración");
        svc.resumen(1, t(1, 9));
        chk(base.asigs.stream().anyMatch(a -> a.userId == 1 && a.misionId == idSaga && a.estado.equals("COMPLETADO")), "la completada queda COMPLETADO");
        Memoria.AsigM nuevoActual = asig(1, "SAGA", "ACTUAL");
        chk(nuevoActual != null && nuevoActual.misionId == idSagaSig, "la que era siguiente pasa a ser la actual");
        Map<String, Object> m2 = rotativas(1, t(1, 9)).stream().filter(m -> ((Number) m.get("id")).longValue() == idSagaSig).findFirst().orElse(null);
        chk(m2 != null && (Integer) m2.get("progreso") == 0, "y arranca desde cero (las películas que votó cuando era «siguiente» no cuentan)");
        chk(asig(1, "SAGA", "SIGUIENTE") != null && asig(1, "SAGA", "SIGUIENTE").misionId != idSaga && asig(1, "SAGA", "SIGUIENTE").misionId != idSagaSig, "y se sortea como siguiente la tercera saga, la única que todavía no se le había ofrecido");

        System.out.println("=== 27) La ✕: descartar la actual");
        long idActor = asig(1, "ACTOR", "ACTUAL").misionId, idActorSig = asig(1, "ACTOR", "SIGUIENTE").misionId;
        chk(svc.descartar(1, idActor, t(1, 10)), "descartar la actual devuelve true");
        chk(base.asigs.stream().anyMatch(a -> a.userId == 1 && a.misionId == idActor && a.estado.equals("DESCARTADO")), "queda DESCARTADO");
        chk(asig(1, "ACTOR", "ACTUAL").misionId == idActorSig, "pasa la siguiente, como si hubiera completado la anterior");
        chk(rotativas(1, t(1, 11)).stream().noneMatch(m -> ((Number) m.get("id")).longValue() == idActor) && lista(svc.completo(1, t(1, 11)).get("proximas")).stream().noneMatch(m -> ((Number) m.get("id")).longValue() == idActor), "la descartada no aparece más, ni en curso ni en próximas");
        long pelActor = idActor == 111 ? 8000 : 8100; voto(1, pelActor, t(1, 12));
        chk(base.filas.stream().noneMatch(f -> f.userId == 1 && f.misionId == idActor), "y votar películas de ese actor ya no suma nada");
        chk(asig(1, "ACTOR", "SIGUIENTE") == null, "con solo 2 actores en el catálogo, no queda otro para sortear");
        chk(!svc.descartar(1, idActor, t(1, 13)), "descartar dos veces la misma devuelve false");

        System.out.println("=== 28) Descartar la siguiente la reemplaza");
        montarRotacion(); svc.completo(1, t(0, 9));
        long sigGen = asig(1, "GENERO", "SIGUIENTE").misionId, actGen = asig(1, "GENERO", "ACTUAL").misionId;
        chk(svc.descartar(1, sigGen, t(0, 10)) && asig(1, "GENERO", "ACTUAL").misionId == actGen, "descartar la SIGUIENTE no toca la actual");
        chk(asig(1, "GENERO", "SIGUIENTE") == null, "con 2 géneros, al descartar el segundo no queda otro para ofrecer");

        System.out.println("=== 29) Qué NO se puede descartar");
        montarRotacion(); base.misiones.add(M(900, "Común", "VOTOS", 5, "PERMANENTE", null, null, null, false, S("Común", "votos"), 1, 1, null, null, "VISIBLE", null, null, 50)); svc.completo(1, t(0, 9));
        chk(!svc.descartar(1, 900, t(0, 10)), "una misión común no se puede descartar");
        long ajena = base.misiones.stream().filter(x -> x.esRotativa() && base.asigs.stream().noneMatch(a -> a.userId == 1 && a.misionId == x.id())).findFirst().get().id();
        chk(!svc.descartar(1, ajena, t(0, 11)), "una rotativa que no tiene asignada no se puede descartar");
        chk(!svc.descartar(1, 99999, t(0, 12)), "un id que no existe, tampoco");

        System.out.println("=== 30) Restaurar una descartada");
        long dsc = asig(1, "SAGA", "ACTUAL").misionId; svc.descartar(1, dsc, t(0, 13));
        chk(svc.descartadas(1).size() == 1 && ((Number) svc.descartadas(1).get(0).get("id")).longValue() == dsc && svc.descartadas(1).get(0).get("nombre") != null, "«Descartadas» la lista con su nombre");
        long actualAntes = asig(1, "SAGA", "ACTUAL").misionId, siguienteAntes = asig(1, "SAGA", "SIGUIENTE").misionId;
        chk(svc.restaurar(1, dsc, t(0, 14)) && svc.descartadas(1).isEmpty(), "restaurarla la saca de la lista");
        chk(asig(1, "SAGA", "ACTUAL") != null && asig(1, "SAGA", "ACTUAL").misionId == dsc, "la recuperada vuelve a ser la ACTUAL de su familia (no queda en el olvido)");
        chk(enCursoDe(1, nombreMision(dsc), t(0, 14)) != null || lista(svc.completo(1, t(0, 14)).get("enCurso")).stream().anyMatch(m -> ((Number) m.get("id")).longValue() == dsc), "y aparece en «En curso» del modal");
        chk(asig(1, "SAGA", "SIGUIENTE") != null && asig(1, "SAGA", "SIGUIENTE").misionId == actualAntes, "la que estaba como actual pasa a ser la siguiente");
        chk(base.asigs.stream().noneMatch(a -> a.userId == 1 && a.misionId == siguienteAntes), "la que estaba como siguiente se suelta: vuelve al sorteo y no queda en «Misiones que quitaste»");
        chk(svc.descartadas(1).isEmpty(), "no queda nada por recuperar");
        long unica = asig(1, "SAGA", "ACTUAL").misionId; chk(svc.descartar(1, unica, t(0, 16)) && svc.restaurar(1, unica, t(0, 17)) && asig(1, "SAGA", "ACTUAL").misionId == unica, "se puede quitar y recuperar de nuevo, y vuelve cada vez");
        chk(!svc.restaurar(1, 900, t(0, 15)), "restaurar algo que no estaba descartado devuelve false");

        System.out.println("=== 31) Sortea sin repetir hasta agotar, y no falla cuando se agota");
        montarRotacion(); Set<Long> vistos = new HashSet<>();
        for (int i = 0; i < 6; i++) { svc.completo(1, t(i, 9)); Memoria.AsigM a = asig(1, "SAGA", "ACTUAL"); if (a == null) break; chk(vistos.add(a.misionId), "ronda " + (i + 1) + ": la saga ofrecida (" + a.misionId + ") es nueva, no se repite"); svc.descartar(1, a.misionId, t(i, 10)); }
        chk(vistos.equals(Set.of(101L, 102L, 103L)), "se ofrecieron las 3 sagas, cada una exactamente una vez");
        chk(asig(1, "SAGA", "ACTUAL") == null && rotativas(1, t(9, 9)).stream().noneMatch(m -> "SAGA".equals(m.get("familia"))), "agotada la lista, no hay misión de saga y nada falla");

        System.out.println("=== 32) No se ofrece lo que ya tiene el sello, ni lo que el admin quitó");
        montarRotacion(); base.otorgarSello(3, base.misiones.stream().filter(x -> x.id() == 103).findFirst().get().sello().id(), 1, 103, t(0, 8));
        svc.completo(3, t(0, 9)); chk(base.asigs.stream().noneMatch(a -> a.userId == 3 && a.misionId == 103), "si ya tiene el sello de la saga Matrix, no se le ofrece");
        base.objetivos.stream().filter(o -> o.misionId == 101).forEach(o -> o.excluido = true);
        svc.completo(4, t(0, 9)); chk(base.asigs.stream().noneMatch(a -> a.userId == 4 && a.misionId == 101), "un objetivo excluido por el admin no se le ofrece a nadie");

        System.out.println("=== 33) Si el admin desactiva una misión asignada, pasa la siguiente");
        montarRotacion(); svc.completo(1, t(0, 9)); long actual = asig(1, "GENERO", "ACTUAL").misionId, sigu = asig(1, "GENERO", "SIGUIENTE").misionId;
        base.misiones.removeIf(x -> x.id() == actual); svc.completo(1, t(0, 10));
        chk(base.asigs.stream().anyMatch(a -> a.userId == 1 && a.misionId == actual && a.estado.equals("CERRADO")) && asig(1, "GENERO", "ACTUAL").misionId == sigu, "la desactivada queda CERRADO y la siguiente pasa a actual");
        System.out.println("=== 34) Cada familia es independiente y no se consulta a TMDB por todo el catálogo");
        montarRotacion(); svc.completo(1, t(0, 9)); long sagaAntes = asig(1, "SAGA", "ACTUAL").misionId, actorAntes = asig(1, "ACTOR", "ACTUAL").misionId;
        svc.descartar(1, sagaAntes, t(0, 10)); chk(asig(1, "ACTOR", "ACTUAL").misionId == actorAntes, "descartar la saga no cambia el actor");
        cat.ultimasCandidatas = -1; cat.llamadas = 0; voto(1, 5555, t(0, 11));
        chk(cat.ultimasCandidatas == 1, "al votar, se consulta a TMDB por UNA sola saga (la del usuario), no por las 3 del catálogo: " + cat.ultimasCandidatas);
        voto(1, 6000, t(0, 12)); chk(cat.llamadas > 0, "y las consultas de género, director y elenco se siguen haciendo para sus misiones");
    }

    // ======================================================================================== audiencia (Premium) e imagen del sello
    static Mision Ma(long id, String t, String audiencia, Sello s, String visibilidad, Integer sellosNecesarios, int orden) {
        return new Mision(id, t, t + ".", "Cumpliste " + t + ".", s.categoria(), "VOTOS", 2, "votos", "PERMANENTE", null, null, null, null, null, false, s, 1, 1.0, null, sellosNecesarios, visibilidad, null, null, null, null, orden, null, null, "PROGRESO", null, audiencia);
    }
    static boolean ve(long u, String titulo, LocalDateTime a) {
        return lista(svc.completo(u, a).get("enCurso")).stream().anyMatch(m -> titulo.equals(m.get("titulo"))) || (svc.completo(u, a).get("destacada") instanceof Map<?, ?> d && titulo.equals(d.get("titulo")));
    }
    static void audienciaEImagen() {
        System.out.println("=== 35) Audiencia: todos · solo Premium · solo no Premium");
        base = new Memoria(); cat = new Catalogo(); int[] consultas = {0}; Set<Long> premium = new HashSet<>(Set.of(90L));
        svc = new MisionService(base, cat, id -> { consultas[0]++; return premium.contains(id); });
        base.misiones.addAll(List.of(Ma(1, "Para todos", "TODOS", S("Todos", "votos"), "VISIBLE", null, 1), Ma(2, "Solo Premium", "PREMIUM", S("Prem", "votos"), "VISIBLE", null, 2), Ma(3, "Solo gratuitos", "FREE", S("Free", "votos"), "VISIBLE", null, 3)));
        chk(ve(90, "Para todos", t(0, 9)) && ve(90, "Solo Premium", t(0, 9)) && !ve(90, "Solo gratuitos", t(0, 9)), "un usuario Premium ve las de todos y las de Premium, y NO las de solo gratuitos");
        chk(ve(5, "Para todos", t(0, 9)) && !ve(5, "Solo Premium", t(0, 9)) && ve(5, "Solo gratuitos", t(0, 9)), "uno que no es Premium ve las de todos y las de gratuitos, y NO las de Premium");
        voto(5, 100, t(0, 10)); voto(5, 101, t(0, 11));
        chk(base.filas.stream().noneMatch(f -> f.userId == 5 && f.misionId == 2) && base.filas.stream().anyMatch(f -> f.userId == 5 && f.misionId == 3 && f.estado.equals("CUMPLIDA")), "para el gratuito, votar no mueve la misión Premium y sí cumple la de gratuitos");
        voto(90, 100, t(0, 10)); voto(90, 101, t(0, 11));
        chk(base.filas.stream().anyMatch(f -> f.userId == 90 && f.misionId == 2 && f.estado.equals("CUMPLIDA")) && base.filas.stream().noneMatch(f -> f.userId == 90 && f.misionId == 3), "para el Premium es al revés: cumple la Premium y no mueve la de gratuitos");
        premium.add(5L);
        chk(ve(5, "Solo Premium", t(1, 9)) && !ve(5, "Solo gratuitos", t(1, 9)), "si el usuario pasa a ser Premium, el panel de misiones se acomoda solo");
        premium.remove(90L);
        chk(!ve(90, "Solo Premium", t(1, 9)) && ve(90, "Solo gratuitos", t(1, 9)), "y si se le vence el Premium, se le esconde la de Premium (no se pierde nada: el sello ganado queda en Mis logros)");
        base = new Memoria(); consultas[0] = 0; svc = new MisionService(base, cat, id -> { consultas[0]++; return false; });
        base.misiones.add(Ma(1, "Para todos", "TODOS", S("Todos", "votos"), "VISIBLE", null, 1)); svc.completo(5, t(0, 9)); voto(5, 100, t(0, 10));
        chk(consultas[0] == 0, "si ninguna misión tiene audiencia, NO se consulta si el usuario es Premium (cero consultas extra)");
        base = new Memoria(); svc = new MisionService(base, cat, id -> { throw new IllegalStateException("la base se cayó"); });
        base.misiones.addAll(List.of(Ma(1, "Para todos", "TODOS", S("Todos", "votos"), "VISIBLE", null, 1), Ma(2, "Solo Premium", "PREMIUM", S("Prem", "votos"), "VISIBLE", null, 2)));
        chk(ve(5, "Para todos", t(0, 9)) && !ve(5, "Solo Premium", t(0, 9)), "si no se puede saber si es Premium, se lo trata como no Premium (nada se rompe)");
        base = new Memoria(); svc = new MisionService(base, cat);
        base.misiones.add(Ma(1, "Solo Premium", "PREMIUM", S("Prem", "votos"), "VISIBLE", null, 1));
        chk(!ve(90, "Solo Premium", t(0, 9)), "con el constructor de pruebas (sin datos del usuario) nadie es Premium");

        System.out.println("=== 36) Imagen del sello");
        String URL = "https://res.cloudinary.com/demo/image/upload/v1/misiones/sellos/estreno.png", FONDO = "https://image.tmdb.org/t/p/w780/fondo.jpg";
        base = new Memoria(); svc = new MisionService(base, cat);
        base.misiones.addAll(List.of(Ma(1, "Con imagen", "TODOS", new Sello(501, "Estreno", "especial", "Un sello con imagen.", URL, FONDO), "VISIBLE", null, 1), Ma(2, "Sin imagen", "TODOS", S("Común", "votos"), "VISIBLE", null, 2),
                Ma(3, "Secreta", "TODOS", new Sello(502, "Secreto", "especial", "No se ve.", URL, FONDO + "-secreto"), "MISTERIOSA", 5, 3)));
        Map<String, Object> c = svc.completo(5, t(0, 9)); List<Map<String, Object>> en = lista(c.get("enCurso"));
        Map<?, ?> conImg = (Map<?, ?>) ((Map<?, ?>) (en.stream().filter(m -> m.get("titulo").equals("Con imagen")).findFirst().orElse(((Map<String, Object>) c.get("destacada"))))).get("sello");
        chk(URL.equals(conImg.get("imagenUrl")), "el sello con imagen la trae en la respuesta (sello.imagenUrl)");
        chk(FONDO.equals(conImg.get("fondoUrl")), "y si tiene imagen de fondo (las sagas), también (sello.fondoUrl)");
        Map<?, ?> sinImg = (Map<?, ?>) en.stream().filter(m -> m.get("titulo").equals("Sin imagen")).findFirst().map(m -> m.get("sello")).orElse(((Map<?, ?>) ((Map<String, Object>) c.get("destacada")).get("sello")));
        chk(!sinImg.containsKey("imagenUrl") && !sinImg.containsKey("fondoUrl"), "el que no tiene imagen NO manda los campos (la aplicación lo dibuja con el ícono de su categoría)");
        Map<String, Object> mis = lista(c.get("proximas")).stream().filter(m -> "MISTERIOSA".equals(m.get("estado"))).findFirst().get();
        chk(!mis.containsKey("sello") && !mis.toString().contains("cloudinary") && !mis.toString().contains("fondo") && !mis.toString().contains("Secreto") && mis.get("titulo").equals("???"), "una misión misteriosa NO filtra ni la imagen, ni el nombre del sello, ni su título");
        voto(5, 1, t(0, 10)); voto(5, 2, t(0, 11));
        Map<String, Object> r = svc.resumen(5, t(0, 12)); Map<?, ?> cum = (Map<?, ?>) ((Map<?, ?>) lista(r.get("cumplidasSinVer")).get(0)).get("sello");
        chk(URL.equals(cum.get("imagenUrl")), "la pantalla de «Misión cumplida» también lleva la imagen del sello");
        List<Map<String, Object>> gan = lista(svc.logros(5).get("sellos"));
        chk(gan.size() == 2 && gan.stream().filter(s -> URL.equals(s.get("imagenUrl"))).count() == 1 && gan.stream().filter(s -> !s.containsKey("imagenUrl")).count() == 1, "en Mis logros, el sello con imagen la trae y el común no (2 sellos ganados, 1 con imagen)");
    }

    public static void main(String[] args) {
        System.out.println("=== 1) «Tres al hilo»: de votar a la celebración");
        montar();
        voto(1, 2000, t(0, 10));
        Map<String, Object> r = svc.resumen(1, t(0, 10));
        chk(base.nivelesDeSellos(1).size() == 1, "el PRIMER voto del usuario ya le dio el sello «Primer voto» (misión de 1 voto)");
        chk("Tres al hilo".equals(titulo(r, "destacada")) && (Integer) mapa(r.get("destacada")).get("progreso") == 1, "tras 1 voto la más fácil es «Tres al hilo» 1/3");
        voto(1, 2001, t(0, 11)); voto(1, 2002, t(0, 12));
        r = svc.resumen(1, t(0, 12));
        List<Map<String, Object>> sinVer = lista(r.get("cumplidasSinVer"));
        chk(sinVer.size() == 2, "con el 3.er voto hay 2 celebraciones pendientes: «Primer voto» y «Tres al hilo»: " + sinVer.stream().map(x -> x.get("titulo")).toList());
        Map<String, Object> tres = sinVer.stream().filter(x -> x.get("titulo").equals("Tres al hilo")).findFirst().get();
        chk(mapa(tres.get("sello")).get("nombre").equals("Tres al hilo") && tres.get("descripcion").equals("Cumpliste Tres al hilo.") && mapa(tres.get("siguiente")).get("titulo") != null, "la celebración trae sello, texto y la siguiente misión");
        chk(!"Tres al hilo".equals(titulo(r, "destacada")), "la destacada pasó a otra misión: «" + titulo(r, "destacada") + "»");
        // visto: solo una vez y solo el dueño
        montar(); voto(1, 2000, t(0, 10)); voto(1, 2001, t(0, 11)); voto(1, 2002, t(0, 12));
        long idT = lista(svc.resumen(1, t(0, 12)).get("cumplidasSinVer")).stream().filter(x -> x.get("titulo").equals("Tres al hilo")).map(x -> ((Number) x.get("id")).longValue()).findFirst().get();
        chk(!svc.visto(2, idT), "otro usuario NO puede marcar como vista la celebración ajena");
        chk(svc.visto(1, idT), "el dueño la marca como vista");
        chk(!svc.visto(1, idT), "y la segunda vez no hace nada");
        chk(lista(svc.resumen(1, t(0, 13)).get("cumplidasSinVer")).stream().noneMatch(x -> x.get("titulo").equals("Tres al hilo")), "ya no aparece entre las pendientes");
        voto(1, 2003, t(0, 14)); voto(1, 2004, t(0, 15));
        chk(base.sellosGanados(1, 99).stream().filter(s -> s.nombre().equals("Tres al hilo")).count() == 1, "votar más no vuelve a otorgar el sello");
        chk(lista(svc.completo(1, t(0, 15)).get("enCurso")).stream().noneMatch(m -> m.get("titulo").equals("Tres al hilo")), "y la misión ganada deja de ofrecerse");

        System.out.println("=== 2) La misión del día se reinicia");
        montar(); voto(1, 2000, t(0, 10)); voto(1, 2001, t(0, 11));
        chk(progresoDe(1, 1, t(0, 11)) == 2, "el día 1: 2 votos = 2 de 3");
        voto(1, 2002, t(1, 9));
        chk(progresoDe(1, 1, t(1, 9)) == 1, "al otro día arranca de nuevo: 1 de 3 (no 3)");
        voto(1, 2003, t(1, 10)); voto(1, 2004, t(1, 11));
        chk(base.nivelesDeSellos(1).size() == 2, "y se cumple con 3 votos en ese mismo día (sellos: «Primer voto» + «Tres al hilo»)");

        System.out.println("=== 3) Misión cualitativa: Maratón de terror");
        montar();
        for (int i = 0; i < 7; i++) { voto(1, 1000 + i, t(0, 8 + i)); if (i % 2 == 0) voto(1, 2000 + i, t(0, 8 + i)); }
        chk(progresoDe(1, 4, t(0, 20)) == 7, "7 votos de terror intercalados con comedias = 7 de 10 (las comedias no cuentan)");
        voto(1, 1007, t(1, 9)); voto(1, 1008, t(1, 10));
        chk(progresoDe(1, 4, t(1, 10)) == 9, "es permanente: el avance del día anterior se conserva (9 de 10)");
        votoSerie(1, 9999, t(1, 11));
        chk(progresoDe(1, 4, t(1, 11)) == 9, "votar una serie no cuenta para una misión de películas");
        voto(1, 1009, t(1, 12));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Terror")), "el 10.º voto de terror otorga el sello");

        System.out.println("=== 4) Misión «distintas»: Cine de autor (Nolan)");
        montar();
        for (int i = 0; i < 3; i++) comentario(1, 4000, t(0, 9 + i));
        chk(progresoDe(1, 5, t(0, 12)) == 1, "3 comentarios a LA MISMA película de Nolan cuentan 1 solo");
        comentario(1, 5000, t(0, 13));
        chk(progresoDe(1, 5, t(0, 13)) == 1, "un comentario a una película de otro director no cuenta");
        for (int i = 1; i <= 3; i++) comentario(1, 4000 + i, t(0, 13 + i));
        chk(progresoDe(1, 5, t(0, 17)) == 4, "con 3 películas más de Nolan van 4 títulos distintos");
        chk(base.sellosGanados(1, 99).stream().noneMatch(s -> s.nombre().equals("Autor")), "todavía no hay sello");
        comentario(1, 4004, t(0, 18));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Autor")), "con el 5.º título distinto se otorga «Autor»");

        System.out.println("=== 5) Racha de días");
        montar();
        for (int d = 0; d < 4; d++) voto(1, 2000 + d, t(d, 10));
        chk(progresoDe(1, 3, t(3, 11)) == 4, "4 días seguidos = 4 de 7");
        chk(progresoDe(1, 3, t(4, 8)) == 4, "al día siguiente, antes de hacer nada, la racha sigue viva (4)");
        chk(progresoDe(1, 3, t(5, 8)) == -1 || progresoDe(1, 3, t(5, 8)) == 0, "si pasa un día entero sin actividad, se corta");
        for (int d = 4; d < 7; d++) comentario(1, 2000 + d, t(d, 10));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Constante")), "7 días seguidos (votando Y comentando) otorgan «Constante»");
        montar(); voto(2, 2000, t(0, 10)); voto(2, 2001, t(1, 10)); voto(2, 2002, t(3, 10));
        chk(progresoDe(2, 3, t(3, 11)) == 1, "un hueco de un día reinicia la racha (1)");

        System.out.println("=== 6) Evento con fechas");
        montar();
        voto(1, 3000, t(5, 12));
        chk(lista(svc.completo(1, t(5, 12)).get("proximas")).stream().anyMatch(m -> "PROGRAMADA".equals(m.get("estado")) && m.get("comienza").equals(D0.plusDays(6).toString())), "antes de empezar figura como PROGRAMADA con su fecha");
        chk(base.filas.stream().noneMatch(f -> f.misionId == 6), "un voto ANTES del evento no cuenta");
        voto(1, 3001, t(6, 12));
        chk(progresoDe(1, 6, t(6, 12)) == 1, "durante el evento sí cuenta (1 de 5)");
        for (int i = 2; i < 5; i++) voto(1, 3000 + i, t(7, 9 + i));
        chk(progresoDe(1, 6, t(7, 14)) == 4, "va por 4 de 5");
        voto(1, 3005, t(13, 9));
        chk(base.sellosGanados(1, 99).stream().noneMatch(s -> s.nombre().equals("Suspenso")), "un voto DESPUÉS de que terminó no cuenta, y no se cumple");
        chk(lista(svc.completo(1, t(13, 9)).get("enCurso")).stream().noneMatch(m -> m.get("titulo").equals("Semana del suspenso")), "el evento vencido desaparece");

        System.out.println("=== 7) Prerrequisito y ciclo semanal");
        montar();
        Map<String, Object> c = svc.completo(1, t(0, 10));
        chk(lista(c.get("proximas")).stream().anyMatch(m -> "BLOQUEADA".equals(m.get("estado")) && m.get("titulo").equals("Crítico en serie") && m.get("desbloqueo").equals("Se desbloquea al ganar el sello «Fan de series».")), "«Crítico en serie» aparece BLOQUEADA y dice qué hay que ganar");
        for (int i = 0; i < 5; i++) votoSerie(1, 100 + i, t(0, 9 + i));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Fan de series")), "5 series en la semana otorgan «Fan de las series»");
        chk(titulos(svc.completo(1, t(1, 9)).get("enCurso")).contains("Crítico en serie"), "y «Crítico en serie» se desbloquea y pasa a «en curso»");
        montar(); for (int i = 0; i < 3; i++) votoSerie(2, 100 + i, t(0, 9 + i));
        for (int i = 0; i < 2; i++) votoSerie(2, 200 + i, t(7, 9 + i));       // t(7) = lunes de la semana siguiente
        chk(base.sellosGanados(2, 99).stream().noneMatch(s -> s.nombre().equals("Fan de series")) && progresoDe(2, 7, t(7, 11)) == 2, "3 series una semana + 2 la siguiente NO suman 5: el ciclo semanal se reinicia (2 de 5)");

        System.out.println("=== 8) Misteriosa: no filtra nada hasta revelarse");
        montar();
        Map<String, Object> cm = svc.completo(1, t(0, 8));
        Map<String, Object> sorpresa = lista(cm.get("proximas")).stream().filter(m -> "MISTERIOSA".equals(m.get("estado"))).findFirst().get();
        chk(sorpresa.get("titulo").equals("???") && !sorpresa.containsKey("sello") && !sorpresa.containsKey("descripcion") && mapa(sorpresa.get("misterio")).get("sellosNecesarios").equals(3), "se ve como «???», sin sello ni descripción, con la cuenta regresiva");
        chk(mapa(cm.get("sellos")).get("porDescubrir").equals(1), "y cuenta como 1 sello por descubrir");
        voto(1, 2000, t(0, 9)); voto(1, 2001, t(0, 10));
        chk(base.filas.stream().noneMatch(f -> f.misionId == 9), "mientras está misteriosa, los votos no la hacen avanzar");
        voto(1, 2002, t(0, 11)); comentario(1, 2000, t(0, 12)); comentario(1, 2001, t(0, 13));
        chk(base.nivelesDeSellos(1).size() >= 3, "el usuario junta 3 sellos: " + base.nivelesDeSellos(1).size());
        chk(titulos(svc.completo(1, t(0, 14)).get("enCurso")).contains("Misión sorpresa"), "con 3 sellos la misteriosa se REVELA: ahora se ve su nombre y está en curso");
        chk(lista(svc.completo(1, t(0, 14)).get("proximas")).stream().noneMatch(m -> "MISTERIOSA".equals(m.get("estado"))), "y sale de «próximas»");

        System.out.println("=== 9) Niveles de un sello");
        montar(); voto(1, 2000, t(0, 9));
        chk(base.sellos.get(1L).values().stream().anyMatch(v -> v[0] == 1), "«Primer voto» da el sello en nivel 1");
        chk(titulos(svc.completo(1, t(0, 9)).get("enCurso")).contains("Voto x3"), "se desbloquea la misión de nivel 2");
        voto(1, 2001, t(0, 10)); voto(1, 2002, t(0, 11)); voto(1, 2003, t(0, 12));
        chk(base.sellos.get(1L).values().stream().anyMatch(v -> v[0] == 2), "al cumplirla, el MISMO sello sube a nivel 2");
        chk(svc.logros(1).get("sellos") instanceof List<?> l && lista(l).stream().anyMatch(s -> Integer.valueOf(2).equals(s.get("nivel"))), "y Mis logros lo informa con nivel 2");

        System.out.println("=== 10) Lo que ve Mi Sala y el modal");
        montar(); voto(1, 2000, t(0, 9)); voto(1, 2001, t(0, 10)); voto(1, 2002, t(0, 11));
        Map<String, Object> lg = svc.logros(1);
        chk(lg.get("cumplidas").equals(2) && lista(lg.get("sellos")).size() == 2 && lista(lg.get("sellos")).get(0).containsKey("descripcion") && lista(lg.get("sellos")).get(0).containsKey("fecha"), "«Mis logros» trae cantidad, sellos con descripción y fecha: " + lg.get("cumplidas"));
        Map<String, Object> cp = svc.completo(1, t(0, 12));
        chk(mapa(cp.get("sellos")).get("total").equals(2) && lista(mapa(cp.get("sellos")).get("recientes")).size() == 2 && !lista(mapa(cp.get("sellos")).get("recientes")).get(0).containsKey("descripcion"), "el modal trae el total y los recientes (sin descripción)");
        List<String> orden = titulos(cp.get("enCurso"));
        chk(orden.get(0).equals(titulo(cp, "destacada")), "la primera de «en curso» es la destacada: " + orden);
        chk(lista(cp.get("proximas")).stream().map(m -> (String) m.get("estado")).toList().equals(List.of("BLOQUEADA", "PROGRAMADA", "MISTERIOSA")), "próximas: bloqueada, programada y misteriosa");
        chk(svc.resumen(99, t(0, 12)).get("destacada") != null && ((List<?>) svc.resumen(99, t(0, 12)).get("cumplidasSinVer")).isEmpty(), "un usuario sin actividad igual recibe su misión destacada y ninguna celebración");

        System.out.println("=== 11) Trivia y otros tipos de actividad");
        montar(); svc.procesar(new ActividadEvento(1, ActividadEvento.Tipo.TRIVIA, ActividadEvento.Contenido.PELICULA, null), t(0, 9));
        chk(progresoDe(1, 12, t(0, 9)) == 1, "un acierto de trivia mueve «Mente de trivia» (1 de 2)");
        svc.procesar(new ActividadEvento(1, ActividadEvento.Tipo.TRIVIA, ActividadEvento.Contenido.PELICULA, null), t(0, 10));
        chk(base.sellosGanados(1, 99).stream().anyMatch(s -> s.nombre().equals("Trivia")), "y con el segundo se otorga el sello");
        chk(base.dias.get(1L).size() == 1, "además marca el día como activo para la racha");

        System.out.println("=== 12) Robustez: una falla de las misiones NUNCA rompe lo demás");
        montar(); base.fallar = true;
        boolean lanzo = false; try { svc.alRegistrarActividad(ActividadEvento.votoPelicula(1, 2000L)); } catch (Exception e) { lanzo = true; }
        chk(!lanzo, "si la base de misiones falla, alRegistrarActividad NO lanza (el voto ya está guardado)");
        montar(); cat.caido = true;
        for (int i = 0; i < 3; i++) voto(1, 1000 + i, t(0, 9 + i));
        chk(base.filas.stream().noneMatch(f -> f.misionId == 4) && base.filas.stream().anyMatch(f -> f.misionId == 1 && f.progreso == 3), "si TMDB no responde, las misiones de género no cuentan pero las demás siguen funcionando");
        montar(); svc.procesar(new ActividadEvento(1, ActividadEvento.Tipo.VOTO, ActividadEvento.Contenido.PELICULA, null), t(0, 9));
        chk(base.dias.get(1L).size() == 1, "un evento sin id de película no rompe nada");
        montar(); voto(1, 2000, t(0, 9)); voto(1, 2001, t(0, 10)); voto(1, 2002, t(0, 11));
        base.misiones.removeIf(m -> m.id() == 1);
        Map<String, Object> rr = svc.resumen(1, t(0, 12));
        chk(lista(rr.get("cumplidasSinVer")).stream().noneMatch(x -> x.get("titulo").equals("Tres al hilo")), "una cumplida de una misión que el admin desactivó después se omite sin error");
        montar();
        chk(svc.resumen(1, t(0, 8)).containsKey("destacada") && ((List<?>) svc.resumen(1, t(0, 8)).get("cumplidasSinVer")).isEmpty(), "el usuario nuevo ve su primera misión (la más fácil) sin celebraciones");
        base.misiones.clear();
        chk(!svc.resumen(1, t(0, 8)).containsKey("destacada") && svc.completo(1, t(0, 8)).get("enCurso") instanceof List<?> l && l.isEmpty(), "sin misiones cargadas: no hay destacada (la tarjeta se oculta) y las listas vienen vacías");

        nuevas();
        escaleras();
        rotacion();
        audienciaEImagen();

        System.out.println("\n" + ok + " verificaciones correctas, " + fal + " fallas");
        if (fal > 0) System.exit(1);
    }
}

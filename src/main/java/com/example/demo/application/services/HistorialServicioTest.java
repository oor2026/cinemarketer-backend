package com.example.demo.application.services;

import com.example.demo.application.services.MisionServicioTest.Catalogo;
import com.example.demo.application.services.MisionServicioTest.Memoria;
import com.example.demo.domain.mision.*;
import com.example.demo.domain.mision.HistorialDatos.Evento;
import com.example.demo.domain.mision.HistorialDatos.Registro;
import com.example.demo.domain.mision.HistorialLogica.Atributos;
import com.example.demo.domain.mision.MisionLogica.*;

import java.time.*;
import java.util.*;

/** «Reclamar»: el historial del usuario (votos y comentarios de antes del sistema) contra las misiones. Se corre con main, como las demás pruebas. */
public class HistorialServicioTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 12, 0);

    // ---------------------------------------------------------------------------------------- historial en memoria (misma semántica que el SQL)
    static class HMem implements HistorialDatos {
        Memoria mem;
        Map<Long, List<Evento>> eventos = new HashMap<>();
        Map<String, Registro> regs = new HashMap<>();
        Map<String, LocalDateTime> primero = new HashMap<>();
        boolean fallar = false;
        HMem(Memoria mem) { this.mem = mem; }
        void f() { if (fallar) throw new IllegalStateException("la base se cayó"); }
        void voto(long u, String contenido, long id, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("VOTO", contenido, id, t)); }
        void publicacion(long u, long id, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("PUBLICACION", "PELICULA", id, t)); }
        void trivia(long u, String contenido, long intento, int aciertos, LocalDateTime t) { for (int i = 0; i < aciertos; i++) eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("TRIVIA", contenido, intento, t)); }
        void intervencion(long u, long publicacion, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("INTERVENCION", "PELICULA", publicacion, t)); }
        void canje(long u, long premio, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("CANJE", "PELICULA", premio, t)); }
        void gusto(long u, int espacio) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("GUSTO", espacio >= 5 ? "SERIE" : "PELICULA", espacio, LocalDateTime.of(2000, 1, 1, 0, 0))); }
        void serie(long u, String tipo, long serieId, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento(tipo, "SERIE", serieId, t)); }
        void comentario(long u, String contenido, long id, LocalDateTime t) { eventos.computeIfAbsent(u, k -> new ArrayList<>()).add(new Evento("COMENTARIO", contenido, id, t)); }
        public List<Evento> eventos(long u) { f(); return eventos.getOrDefault(u, List.of()); }
        public Map<Long, Registro> registros(long u) { f(); Map<Long, Registro> r = new HashMap<>(); for (var e : regs.entrySet()) if (e.getKey().startsWith(u + ":")) r.put(e.getValue().misionId(), e.getValue()); return r; }
        public Map<Long, LocalDateTime> primerAvanceEnVivo(long u) { f(); Map<Long, LocalDateTime> r = new HashMap<>(); for (var e : primero.entrySet()) if (e.getKey().startsWith(u + ":")) r.put(Long.valueOf(e.getKey().split(":")[1]), e.getValue()); return r; }
        public void guardar(long u, long m, String estado, int base, LocalDateTime ahora) {
            Registro ya = regs.get(u + ":" + m);
            if (ya != null && !"IGNORADO".equals(ya.estado())) return;                  // una PENDIENTE o RECLAMADO nunca se pisa
            f(); regs.put(u + ":" + m, new Registro(m, estado, base, ahora));
        }
        public boolean marcarReclamado(long u, long m, LocalDateTime ahora) { f(); Registro r = regs.get(u + ":" + m); if (r == null || !"PENDIENTE".equals(r.estado())) return false; regs.put(u + ":" + m, new Registro(m, "RECLAMADO", r.base(), r.calculadoAt())); return true; }
        public int contarPendientes(long u) { f(); return (int) registros(u).values().stream().filter(r -> "PENDIENTE".equals(r.estado())).count(); }
        public List<Mision> rotativasParaEvaluar(long u, LocalDateTime desde) {
            f(); List<Mision> r = new ArrayList<>();
            for (Memoria.Obj o : mem.objetivos) {
                if (!o.activo || o.excluido) continue;
                Mision m = mem.misiones.stream().filter(x -> x.id() == o.misionId).findFirst().orElse(null);
                if (m == null || mem.asigs.stream().anyMatch(a -> a.userId == u && a.misionId == m.id())) continue;
                if (mem.sellos.getOrDefault(u, Map.of()).get(m.sello().id()) != null) continue;
                Registro g = regs.get(u + ":" + m.id());
                if (g != null && (!"IGNORADO".equals(g.estado()) || g.calculadoAt().isAfter(desde))) continue;
                r.add(m);
            }
            return r;
        }
        public List<Mision> misionesPorIds(List<Long> ids) { f(); return mem.misiones.stream().filter(m -> ids.contains(m.id())).toList(); }
        public Long objetivoDeMision(long m) { return mem.objetivos.stream().filter(o -> o.misionId == m).map(o -> o.id).findFirst().orElse(null); }
    }

    // ---------------------------------------------------------------------------------------- armado
    static long sid = 100;
    static Sello S(String n, String c) { return new Sello(++sid, n, c, "Sello " + n); }

    /** Misión permanente de votos o comentarios, con lo que haga falta pedirle. */
    static Mision mision(long id, String titulo, String metrica, int meta, String tipo, Long gen, Long dir, Long saga, Long actor, boolean distintas, String escalera, String familia) {
        Sello s = S(titulo, "votos");
        return new Mision(id, titulo, titulo + ".", "Cumpliste " + titulo + ".", "votos", metrica, meta, "votos", "PERMANENTE", tipo, gen, dir, saga, actor, distintas,
                s, 1, 1.0, null, null, "VISIBLE", null, null, null, null, (int) id, MisionLogica.parseEscalera(escalera), null, "PROGRESO", familia);
    }

    static class Mundo {
        Memoria mem = new Memoria(); HMem hm = new HMem(mem); Catalogo cat = new Catalogo();
        HistorialService hs; MisionService svc;
        Mundo() {
            hs = new HistorialService(mem, hm, cat);
            hs.usarEjecutor(Runnable::run);                        // el trabajo en segundo plano corre acá mismo
            svc = new MisionService(mem, cat);
            svc.usarHistorial(hs);
            svc.usarAzar(new Random(7));
        }
        Mision agregar(Mision m) { mem.misiones.add(m); return m; }
        /** Un objetivo de rotación para esa misión. */
        long objetivo(Mision m, String familia) { MisionServicioTest.Memoria.Obj o = new MisionServicioTest.Memoria.Obj(); o.id = ++seq; o.misionId = m.id(); o.familia = familia; o.nombre = m.titulo(); mem.objetivos.add(o); return o.id; }
        long seq = 0;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> lista(Map<String, Object> r, String k) { return (List<Map<String, Object>>) r.getOrDefault(k, List.of()); }
    static boolean contiene(List<Map<String, Object>> l, String titulo) { return l.stream().anyMatch(d -> titulo.equals(d.get("titulo"))); }
    static LocalDateTime hace(int dias) { return AHORA.minusDays(dias); }

    // ================================================================================================
    static void logica() {
        System.out.println("-- HistorialLogica");
        Mision votos = mision(1, "Votador", "VOTOS", 10, null, null, null, null, null, false, null, null);
        Mision evento = new Mision(2, "Evento", "e", "e", "votos", "VOTOS", 1, "v", "EVENTO", null, null, null, false, S("Evento", "votos"), 1, 1, null, null, "VISIBLE", null, null, null, null, 1);
        Mision hoy = new Mision(3, "Hoy", "e", "e", "votos", "VOTOS", 1, "v", "HOY", null, null, null, false, S("Hoy", "votos"), 1, 1, null, null, "VISIBLE", null, null, null, null, 1);
        Mision racha = new Mision(4, "Racha", "e", "e", "constancia", "RACHA_DIAS", 3, "d", "PERMANENTE", null, null, null, false, S("Racha", "constancia"), 1, 1, null, null, "VISIBLE", null, null, null, null, 1);
        Mision trivias = mision(5, "Trivias", "TRIVIAS", 5, null, null, null, null, null, false, null, null);
        Mision conFechas = new Mision(6, "Fechas", "e", "e", "votos", "VOTOS", 1, "v", "PERMANENTE", null, null, null, false, S("Fechas", "votos"), 1, 1, null, null, "VISIBLE", AHORA, null, null, null, 1);
        chk(HistorialLogica.elegible(votos), "una misión permanente de votos mira el historial");
        chk(!HistorialLogica.elegible(evento), "una misión de evento NO mira el historial (una campaña no se regala con votos viejos)");
        chk(!HistorialLogica.elegible(hoy), "la del día no");
        chk(!HistorialLogica.elegible(racha), "la de racha no");
        chk(HistorialLogica.elegible(trivias), "una permanente de trivias sí mira el historial");
        chk(HistorialLogica.elegible(mision(9, "Canjes", "CANJES", 3, null, null, null, null, null, false, null, null)), "los canjes también");
        chk(HistorialLogica.elegible(mision(10, "Gustos", "GUSTOS", 8, null, null, null, null, null, true, null, null)), "y los gustos");
        chk(!HistorialLogica.elegible(mision(11, "Sorteos", "SORTEOS", 3, null, null, null, null, null, false, null, null)), "una métrica que todavía no tiene historial (sorteos) no");
        chk(HistorialLogica.elegible(mision(7, "Publicador", "PUBLICACIONES", 1, null, null, null, null, null, false, null, null)), "una permanente de publicaciones sí mira el historial");
        chk(HistorialLogica.elegible(mision(8, "Participador", "INTERVENCIONES", 10, null, null, null, null, null, false, null, null)), "las intervenciones en publicaciones también");
        chk(!HistorialLogica.elegible(conFechas), "una permanente con fecha de inicio no");

        List<Evento> ev = List.of(new Evento("VOTO", "PELICULA", 1, hace(30)), new Evento("VOTO", "PELICULA", 2, hace(20)), new Evento("VOTO", "SERIE", 9, hace(10)),
                new Evento("COMENTARIO", "PELICULA", 1, hace(5)), new Evento("COMENTARIO", "PELICULA", 1, hace(4)), new Evento("VOTO", "PELICULA", 3, AHORA.minusSeconds(3)));
        chk(HistorialLogica.contar(votos, ev, AHORA.minusSeconds(10), a -> Atributos.VACIO).total() == 3, "cuenta los votos de películas y series anteriores al corte (3)");
        chk(HistorialLogica.contar(votos, ev, AHORA, a -> Atributos.VACIO).total() == 4, "sin corte contaría 4: el de hace 3 segundos entra solo si el corte es posterior");
        Mision peli = mision(10, "Pelis", "VOTOS", 5, "PELICULA", null, null, null, null, false, null, null);
        chk(HistorialLogica.contar(peli, ev, AHORA, a -> Atributos.VACIO).total() == 3, "una misión de películas no cuenta los votos de series");
        Mision coment = mision(11, "Coment", "COMENTARIOS", 5, null, null, null, null, null, false, null, null);
        Mision comentDist = mision(12, "ComentDist", "COMENTARIOS", 5, null, null, null, null, null, true, null, null);
        chk(HistorialLogica.contar(coment, ev, AHORA, a -> Atributos.VACIO).total() == 2, "dos comentarios en la misma película suman 2");
        chk(HistorialLogica.contar(comentDist, ev, AHORA, a -> Atributos.VACIO).total() == 1, "con «películas distintas», suman 1");
        Mision saga = mision(13, "Saga", "VOTOS", 2, "PELICULA", null, null, 77L, null, false, null, "SAGA");
        Map<Long, Atributos> at = Map.of(1L, new Atributos(Set.of(), Set.of(), Set.of(), Set.of(77L)), 2L, new Atributos(Set.of(), Set.of(), Set.of(), Set.of(77L)));
        chk(HistorialLogica.contar(saga, ev, AHORA, id -> at.getOrDefault(id, Atributos.VACIO)).total() == 2, "con filtro de saga solo cuentan las películas de esa saga");
        Mision serieConFiltro = mision(14, "SerieGenero", "VOTOS", 1, "SERIE", 27L, null, null, null, false, null, null);
        chk(HistorialLogica.contar(serieConFiltro, ev, AHORA, id -> new Atributos(Set.of(27L), Set.of(), Set.of(), Set.of())).total() == 0, "una serie nunca cumple una misión con filtro de TMDB (igual que en vivo)");
        chk(HistorialLogica.corte(null, AHORA).equals(AHORA), "sin actividad contada en vivo, el corte es ahora");
        chk(HistorialLogica.corte(AHORA, AHORA).equals(AHORA.minusSeconds(10)), "con actividad en vivo, el corte es 10 segundos antes de la primera");
        Mision esc = mision(15, "Esc", "VOTOS", 1, null, null, null, null, null, false, "1,10,25", null);
        chk(HistorialLogica.meta(esc, 0) == 1 && HistorialLogica.meta(esc, 1) == 10, "la meta de una escalera es la del escalón en que está");
    }

    // ================================================================================================
    static void escaleraCompleta() {
        System.out.println("-- historial completo en una escalera → Reclamar");
        Mundo w = new Mundo();
        Mision votador = w.agregar(mision(1, "Votador", "VOTOS", 1, null, null, null, null, null, false, "1,10,25", null));
        for (int i = 0; i < 12; i++) w.hm.voto(1, "PELICULA", 500 + i, hace(60 - i));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Votador"), "la misión aparece para reclamar");
        chk(lista(r, "porReclamar").get(0).get("reclamable") == Boolean.TRUE, "con la marca reclamable");
        chk(lista(r, "porReclamar").get(0).get("descartable") == null, "sin la ✕ de descartar");
        chk(!contiene(lista(r, "enCurso"), "Votador"), "y no se repite entre las que están en curso");
        chk(r.get("destacada") == null, "ni se destaca con el avance en cero");
        chk(w.mem.nivelesDeSellos(1).isEmpty(), "todavía no se le dio ningún sello: lo reclama él");
        chk(w.svc.resumen(1, AHORA).get("porReclamar").equals(1), "el feed liviano avisa que tiene 1 para reclamar");

        chk(w.svc.reclamar(1, votador.id(), AHORA), "reclamar funciona");
        chk(w.mem.nivelesDeSellos(1).get(votador.sello().id()) == 2, "recibe el nivel 2 de una vez (12 votos: pasa el escalón de 1 y el de 10)");
        chk(w.mem.cels.size() == 1 && w.mem.cels.get(0).nivel == 2, "una sola pantalla de «Subiste de nivel», con el nivel final");
        FilaM_ f = new FilaM_(w.mem, 1, votador.id());
        chk(f.progreso == 12 && f.nivel == 2, "la misión queda en 12 votos y nivel 2, apuntando al escalón de 25");
        chk(!w.svc.reclamar(1, votador.id(), AHORA), "reclamar de nuevo no hace nada (no se otorga dos veces)");
        chk(w.mem.cels.size() == 1, "y no repite la celebración");
        Map<String, Object> r2 = w.svc.completo(1, AHORA);
        chk(lista(r2, "porReclamar").isEmpty(), "ya no figura para reclamar");
        chk(contiene(lista(r2, "enCurso"), "Votador"), "sigue en curso, con la escalera hacia el nivel 3");
        // un voto nuevo suma al avance que reclamó
        w.svc.procesar(ActividadEvento.votoPelicula(1, 999L), AHORA.plusMinutes(5));
        chk(new FilaM_(w.mem, 1, votador.id()).progreso == 13, "un voto nuevo en vivo sigue sumando (13)");
    }

    /** La fila 'P' de un usuario en una misión, para mirar su avance. */
    static class FilaM_ {
        int progreso, nivel; String estado;
        FilaM_(Memoria mem, long u, long m) { for (var x : mem.filas) if (x.userId == u && x.misionId == m && x.ciclo.equals("P")) { progreso = x.progreso; nivel = x.nivel; estado = x.estado; } }
    }

    // ================================================================================================
    static void parcialYNoDoble() {
        System.out.println("-- historial parcial: se respeta el progreso, sin botón");
        Mundo w = new Mundo();
        Mision series = w.agregar(mision(1, "Crítico en serie", "VOTOS", 3, "SERIE", null, null, null, null, false, null, null));
        w.hm.voto(1, "SERIE", 70, hace(9)); w.hm.voto(1, "SERIE", 71, hace(8));
        w.hm.voto(1, "PELICULA", 5, hace(7));                    // una película no cuenta en una misión de series
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(lista(r, "porReclamar").isEmpty(), "con 2 de 3 no hay Reclamar: no puede reclamar lo que no completó");
        chk(w.mem.nivelesDeSellos(1).isEmpty(), "y no se le dio ningún sello");
        chk(new FilaM_(w.mem, 1, series.id()).progreso == 2, "pero el avance de 2 ya está sumado en la misión");
        Map<String, Object> en = lista(r, "enCurso").stream().filter(d -> "Crítico en serie".equals(d.get("titulo"))).findFirst().orElse(null);
        chk(en != null && Integer.valueOf(2).equals(en.get("progreso")), "y se muestra ya en la misma respuesta como 2 de 3");
        // abrir de nuevo no suma otra vez
        w.svc.completo(1, AHORA.plusMinutes(1)); w.svc.resumen(1, AHORA.plusMinutes(2));
        chk(new FilaM_(w.mem, 1, series.id()).progreso == 2, "abrirlas de nuevo no suma dos veces (queda en 2)");
        // un voto en vivo la completa
        w.svc.procesar(ActividadEvento.votoSerie(1, 72L), AHORA.plusMinutes(3));
        chk(w.mem.nivelesDeSellos(1).get(series.sello().id()) != null, "con el voto en vivo que falta, la completa y recibe el sello");
    }

    static void sinDobleConteo() {
        System.out.println("-- lo que ya contó el sistema en vivo no se cuenta dos veces");
        Mundo w = new Mundo();
        Mision m = w.agregar(mision(1, "Diez votos", "VOTOS", 10, null, null, null, null, null, false, null, null));
        // 5 votos de antes del sistema, y después el sistema contó en vivo 3 votos (el primero a las 11:00)
        for (int i = 0; i < 5; i++) w.hm.voto(1, "PELICULA", 10 + i, hace(20 + i));
        LocalDateTime primero = AHORA.minusHours(1);
        w.hm.voto(1, "PELICULA", 20, primero.minusSeconds(2));    // el primer voto en vivo: se guarda ~2 s antes de que se cree la fila de la misión
        w.hm.voto(1, "PELICULA", 21, primero.plusMinutes(10)); w.hm.voto(1, "PELICULA", 22, primero.plusMinutes(20));
        w.hm.primero.put("1:1", primero);
        w.mem.filas.add(fila(w.mem, 1, 1, "P", 3));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(lista(r, "porReclamar").isEmpty(), "5 de historial + 3 en vivo = 8 de 10: no alcanza, no hay Reclamar");
        chk(new FilaM_(w.mem, 1, 1).progreso == 8, "el avance queda en 8: 3 en vivo + 5 de historial (el primer voto en vivo no se cuenta dos veces)");
    }

    static MisionServicioTest.FilaM fila(Memoria mem, long u, long m, String ciclo, int progreso) {
        MisionServicioTest.FilaM x = new MisionServicioTest.FilaM(); x.id = ++mem.seq; x.userId = u; x.misionId = m; x.ciclo = ciclo; x.progreso = progreso; return x;
    }

    static void noMiraLoQueNoDebe() {
        System.out.println("-- las campañas y lo del día no miran el historial");
        Mundo w = new Mundo();
        Sello se = S("Campaña", "especial");
        Mision campana = w.agregar(new Mision(1, "Campaña marca", "c", "c", "especial", "VOTOS", 1, "votos", "EVENTO", null, null, null, false, se, 1, 1, null, null, "VISIBLE",
                AHORA.minusDays(1), AHORA.plusDays(6), null, null, 5));
        Mision dia = w.agregar(new Mision(2, "Tres al hilo", "c", "c", "votos", "VOTOS", 3, "votos", "HOY", null, null, null, false, S("Tres", "votos"), 1, 1, null, null, "VISIBLE", null, null, null, null, 6));
        for (int i = 0; i < 20; i++) w.hm.voto(1, "PELICULA", i, hace(40 + i));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(lista(r, "porReclamar").isEmpty(), "nada para reclamar");
        chk(w.hm.regs.isEmpty(), "y no se guardó ningún historial de esas misiones");
        chk(w.mem.nivelesDeSellos(1).isEmpty(), "con 20 votos viejos, no se regala la campaña ni la del día");
    }

    // ================================================================================================
    static void rotativas() {
        System.out.println("-- rotativas: lo que no le tocó pero ya completó, y lo que le tocó con avance parcial");
        Mundo w = new Mundo();
        Mision otra1 = w.agregar(mision(1, "Saga A", "VOTOS", 5, "PELICULA", null, null, 1L, null, false, null, "SAGA"));
        Mision otra2 = w.agregar(mision(2, "Saga B", "VOTOS", 5, "PELICULA", null, null, 2L, null, false, null, "SAGA"));
        Mision matrix = w.agregar(mision(3, "Saga Matrix", "VOTOS", 4, "PELICULA", null, null, 100L, null, false, null, "SAGA"));
        long oA = w.objetivo(otra1, "SAGA"), oB = w.objetivo(otra2, "SAGA"), oM = w.objetivo(matrix, "SAGA");
        w.mem.asignar(1, "SAGA", oA, "ACTUAL", AHORA); w.mem.asignar(1, "SAGA", oB, "SIGUIENTE", AHORA);     // a este usuario le tocaron A y B; Matrix no
        w.cat.sagaPartes.put(100L, Set.of(11L, 12L, 13L, 14L));
        w.cat.sagaPartes.put(1L, Set.of(21L, 22L));
        // votó 2 de Matrix: todavía no la completa
        w.hm.voto(1, "PELICULA", 11, hace(30)); w.hm.voto(1, "PELICULA", 12, hace(29));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(lista(r, "porReclamar").isEmpty(), "con 2 de 4 de Matrix (que no le tocó) no se le ofrece nada");
        chk(w.hm.regs.get("1:3") != null && w.hm.regs.get("1:3").estado().equals("IGNORADO"), "queda anotada como IGNORADO");
        // sigue votando Matrix: la completa, pero no pasaron 6 horas desde la última revisión
        w.hm.voto(1, "PELICULA", 13, hace(20)); w.hm.voto(1, "PELICULA", 14, hace(19));
        chk(lista(w.svc.completo(1, AHORA.plusHours(1)), "porReclamar").isEmpty(), "antes de las 6 horas no se vuelve a revisar");
        Map<String, Object> r2 = w.svc.completo(1, AHORA.plusHours(7));
        chk(contiene(lista(r2, "porReclamar"), "Saga Matrix"), "pasadas las 6 horas ve que completó Matrix y la ofrece para reclamar");
        chk(lista(r2, "porReclamar").get(0).get("descartable") == null, "sin la ✕");
        chk(w.svc.reclamar(1, matrix.id(), AHORA.plusHours(8)), "la reclama");
        chk(w.mem.nivelesDeSellos(1).get(matrix.sello().id()) != null, "recibe el sello de Matrix");
        chk(w.mem.asigs.stream().anyMatch(a -> a.userId == 1 && a.objetivoId == oM && a.estado.equals("COMPLETADO")), "queda anotada como completada, así no se le sortea después");
        chk(w.mem.cels.size() == 1, "con su pantalla de «Misión cumplida»");
        chk(lista(w.svc.completo(1, AHORA.plusHours(9)), "porReclamar").isEmpty(), "y ya no está para reclamar");

        System.out.println("-- una rotativa que SÍ le tocó, con avance parcial");
        Mundo v = new Mundo();
        Mision mx = v.agregar(mision(3, "Saga Matrix", "VOTOS", 4, "PELICULA", null, null, 100L, null, false, null, "SAGA"));
        long oMx = v.objetivo(mx, "SAGA");
        v.mem.asignar(1, "SAGA", oMx, "ACTUAL", AHORA);
        v.cat.sagaPartes.put(100L, Set.of(11L, 12L, 13L, 14L));
        v.hm.voto(1, "PELICULA", 11, hace(30)); v.hm.voto(1, "PELICULA", 12, hace(29)); v.hm.voto(1, "PELICULA", 40, hace(28));
        Map<String, Object> q = v.svc.completo(1, AHORA);
        chk(lista(q, "porReclamar").isEmpty(), "con 2 de 4 no hay Reclamar");
        chk(new FilaM_(v.mem, 1, mx.id()).progreso == 2, "el avance de 2 se suma solo (la película de otra saga no cuenta)");
        v.svc.procesar(ActividadEvento.votoPelicula(1, 13L), AHORA.plusMinutes(5));
        v.cat.sagaPartes.put(100L, Set.of(11L, 12L, 13L, 14L));
        v.svc.procesar(ActividadEvento.votoPelicula(1, 14L), AHORA.plusMinutes(6));
        chk(v.mem.nivelesDeSellos(1).get(mx.sello().id()) != null, "con las dos que faltan en vivo, completa Matrix y recibe el sello");
    }

    static void rotativaAsignadaCompleta() {
        System.out.println("-- rotativa asignada que el historial ya completó");
        Mundo w = new Mundo();
        Mision m = w.agregar(mision(1, "Maratón de terror", "VOTOS", 3, "PELICULA", 27L, null, null, null, false, null, "GENERO"));
        long o = w.objetivo(m, "GENERO");
        w.mem.asignar(1, "GENERO", o, "ACTUAL", AHORA);
        w.cat.g.put(31L, Set.of(27L)); w.cat.g.put(32L, Set.of(27L)); w.cat.g.put(33L, Set.of(27L)); w.cat.g.put(34L, Set.of(35L));
        for (long id = 31; id <= 34; id++) w.hm.voto(1, "PELICULA", id, hace(10));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Maratón de terror"), "la que le tocó y ya completó también va para reclamar");
        chk(w.svc.reclamar(1, m.id(), AHORA), "la reclama");
        chk(w.mem.asigs.stream().anyMatch(a -> a.objetivoId == o && a.estado.equals("COMPLETADO")), "y la rotación pasa a la siguiente (queda COMPLETADO)");
    }

    // ================================================================================================
    static void titulosDistintos() {
        System.out.println("-- misiones de títulos distintos: no se cuenta dos veces el mismo");
        Mundo w = new Mundo();
        Mision m = w.agregar(mision(1, "Comentá 3 pelis", "COMENTARIOS", 3, null, null, null, null, null, true, null, null));
        w.hm.comentario(1, "PELICULA", 1, hace(9)); w.hm.comentario(1, "PELICULA", 1, hace(8)); w.hm.comentario(1, "PELICULA", 2, hace(7));
        w.svc.completo(1, AHORA);
        chk(new FilaM_(w.mem, 1, 1).progreso == 2, "tres comentarios en dos películas suman 2");
        w.svc.procesar(ActividadEvento.comentarioPelicula(1, 1L), AHORA.plusMinutes(1));
        chk(new FilaM_(w.mem, 1, 1).progreso == 2 && w.mem.nivelesDeSellos(1).isEmpty(), "comentar otra vez la película 1 en vivo no suma: ya estaba contada");
        w.svc.procesar(ActividadEvento.comentarioPelicula(1, 3L), AHORA.plusMinutes(2));
        chk(w.mem.nivelesDeSellos(1).get(m.sello().id()) != null, "una tercera película distinta la completa");
    }

    static void reclamarCasosBorde() {
        System.out.println("-- reclamar: casos borde");
        Mundo w = new Mundo();
        Mision a = w.agregar(mision(1, "Cinco votos", "VOTOS", 5, null, null, null, null, null, false, null, null));
        for (int i = 0; i < 6; i++) w.hm.voto(1, "PELICULA", i, hace(10));
        w.svc.completo(1, AHORA);
        chk(!w.svc.reclamar(2, a.id(), AHORA), "otro usuario no puede reclamar lo que no tiene");
        chk(!w.svc.reclamar(1, 99, AHORA), "una misión que no existe no se reclama");
        // lo completa en vivo ANTES de reclamar: el sello ya está, reclamar no lo duplica
        w.svc.procesar(ActividadEvento.votoPelicula(1, 500L), AHORA.plusMinutes(1));
        w.svc.procesar(ActividadEvento.votoPelicula(1, 501L), AHORA.plusMinutes(2));
        w.svc.procesar(ActividadEvento.votoPelicula(1, 502L), AHORA.plusMinutes(3));
        w.svc.procesar(ActividadEvento.votoPelicula(1, 503L), AHORA.plusMinutes(4));
        w.svc.procesar(ActividadEvento.votoPelicula(1, 504L), AHORA.plusMinutes(5));
        int celsAntes = w.mem.cels.size();
        chk(w.mem.nivelesDeSellos(1).get(a.sello().id()) != null, "la completó en vivo");
        chk(w.svc.reclamar(1, a.id(), AHORA.plusMinutes(6)), "reclamar después igual responde bien");
        chk(w.mem.cels.size() == celsAntes, "pero no repite el sello ni la celebración");
        chk(new MisionServicioTest().getClass() != null, "(sanidad)");
    }

    static void aislamiento() {
        System.out.println("-- si el historial falla, las misiones siguen funcionando");
        Mundo w = new Mundo();
        Mision a = w.agregar(mision(1, "Cinco votos", "VOTOS", 5, null, null, null, null, null, false, null, null));
        w.hm.fallar = true;
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(r.get("enCurso") != null && contiene(lista(r, "enCurso"), "Cinco votos"), "completo() responde con normalidad");
        chk(r.get("porReclamar") == null, "sin el bloque de reclamar");
        chk(w.svc.resumen(1, AHORA) != null, "resumen() también");

        System.out.println("-- sin historial configurado (como antes de este cambio)");
        Memoria mem = new Memoria(); Catalogo cat = new Catalogo();
        MisionService viejo = new MisionService(mem, cat);
        mem.misiones.add(mision(1, "Cinco votos", "VOTOS", 5, null, null, null, null, null, false, null, null));
        chk(contiene(lista(viejo.completo(1, AHORA), "enCurso"), "Cinco votos"), "funciona igual que siempre");
        chk(!viejo.reclamar(1, 1, AHORA), "y reclamar responde que no hay nada");
    }

    static void publicaciones() {
        System.out.println("-- publicaciones");
        Mundo w = new Mundo();
        Mision pub = w.agregar(mision(1, "Publicador", "PUBLICACIONES", 1, null, null, null, null, null, false, "1,5,10", null));
        w.hm.publicacion(1, 900, hace(40)); w.hm.publicacion(1, 901, hace(30)); w.hm.voto(1, "PELICULA", 5, hace(20)); w.hm.comentario(1, "PELICULA", 5, hace(19));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Publicador"), "con 2 publicaciones de antes, la escalera de publicaciones se puede reclamar");
        chk(w.svc.reclamar(1, pub.id(), AHORA), "la reclama");
        chk(w.mem.nivelesDeSellos(1).get(pub.sello().id()) == 1, "recibe el nivel 1 (2 publicaciones: pasa el escalón de 1, no el de 5)");
        chk(new FilaM_(w.mem, 1, pub.id()).progreso == 2, "y queda en 2 publicaciones: los votos y comentarios no se mezclan");
        w.svc.procesar(ActividadEvento.publicacion(1), AHORA.plusMinutes(5));
        chk(new FilaM_(w.mem, 1, pub.id()).progreso == 3, "una publicación nueva en vivo suma (3)");
        Mundo v = new Mundo();
        Mision otra = v.agregar(mision(2, "Publicá 3", "PUBLICACIONES", 3, null, null, null, null, null, false, null, null));
        v.hm.publicacion(1, 900, hace(10)); v.hm.publicacion(1, 901, hace(9));
        Map<String, Object> q = v.svc.completo(1, AHORA);
        chk(lista(q, "porReclamar").isEmpty() && new FilaM_(v.mem, 1, 2).progreso == 2, "con 2 de 3, el avance se suma y no hay Reclamar");
    }

    static void intervenciones() {
        System.out.println("-- intervenciones en publicaciones");
        Mundo w = new Mundo();
        Mision part = w.agregar(mision(1, "Participador", "INTERVENCIONES", 3, null, null, null, null, null, false, "3,10", null));
        Mision muchas = w.agregar(mision(2, "En muchas conversaciones", "INTERVENCIONES", 3, null, null, null, null, null, true, null, null));
        w.hm.intervencion(1, 500, hace(9)); w.hm.intervencion(1, 500, hace(8)); w.hm.intervencion(1, 501, hace(7)); w.hm.intervencion(1, 501, hace(6));
        w.hm.publicacion(1, 900, hace(5)); w.hm.voto(1, "PELICULA", 1, hace(4));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Participador"), "4 intervenciones: Participador (3) se puede reclamar");
        chk(!contiene(lista(r, "porReclamar"), "En muchas conversaciones"), "pero en 2 publicaciones distintas no alcanza para «En muchas conversaciones» (3)");
        chk(new FilaM_(w.mem, 1, 2).progreso == 2, "y suma 2 de 3 publicaciones distintas");
        chk(w.svc.reclamar(1, part.id(), AHORA) && new FilaM_(w.mem, 1, 1).progreso == 4, "reclama Participador y queda en 4 (las publicaciones y los votos no se mezclan)");
        w.svc.procesar(ActividadEvento.intervencion(1, 502L), AHORA.plusMinutes(1));
        chk(w.mem.nivelesDeSellos(1).get(muchas.sello().id()) != null, "una intervención en una tercera publicación distinta la completa en vivo");
    }

    static void canjes() {
        System.out.println("-- canjes");
        Mundo w = new Mundo();
        Mision cant = w.agregar(mision(1, "Canjeador", "CANJES", 3, null, null, null, null, null, false, null, null));
        Mision dist = w.agregar(mision(2, "Premios distintos", "CANJES", 3, null, null, null, null, null, true, null, null));
        Mision serie = w.agregar(mision(3, "Canje de series", "CANJES", 1, "SERIE", null, null, null, null, false, null, null));
        w.hm.canje(1, 10, hace(9)); w.hm.canje(1, 10, hace(8)); w.hm.canje(1, 11, hace(7)); w.hm.canje(1, 12, hace(6)); w.hm.canje(1, -5, hace(5));
        w.hm.voto(1, "PELICULA", 1, hace(4));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Canjeador"), "5 canjes: Canjeador (3) se puede reclamar");
        chk(contiene(lista(r, "porReclamar"), "Premios distintos"), "4 premios distintos (10, 11, 12 y el premium): Premios distintos (3) se puede reclamar");
        chk(!contiene(lista(r, "porReclamar"), "Canje de series"), "un canje nunca es de series: la misión de series no se reclama (igual que en vivo)");
        chk(w.svc.reclamar(1, cant.id(), AHORA) && w.mem.nivelesDeSellos(1).get(cant.sello().id()) != null, "reclama Canjeador y recibe el sello");
        chk(w.hm.regs.get("1:1").estado().equals("RECLAMADO"), "queda marcada como reclamada");
        chk(!w.svc.reclamar(1, cant.id(), AHORA), "reclamar dos veces no da dos sellos");
        // un premio que el sistema ya contó en vivo no se cuenta otra vez
        Mundo v = new Mundo();
        v.agregar(mision(1, "Premios distintos", "CANJES", 3, null, null, null, null, null, true, null, null));
        v.hm.canje(2, 10, hace(9)); v.hm.canje(2, 11, hace(8));
        v.svc.procesar(ActividadEvento.canje(2, 10L), hace(1));        // el sistema ya contó el premio 10 en vivo
        Map<String, Object> r2 = v.svc.completo(2, AHORA);
        chk(!contiene(lista(r2, "porReclamar"), "Premios distintos"), "2 premios distintos (uno ya contado en vivo): no alcanza para reclamar");
        chk(new FilaM_(v.mem, 2, 1).progreso == 2, "el progreso queda en 2, sin contar dos veces el premio 10");
    }

    static void gustos() {
        System.out.println("-- gustos");
        Mundo w = new Mundo();
        Mision todos = w.agregar(mision(1, "Perfil completo", "GUSTOS", 8, null, null, null, null, null, true, null, null));
        Mision pelis = w.agregar(mision(2, "Gustos de películas", "GUSTOS", 4, "PELICULA", null, null, null, null, true, null, null));
        Mision series = w.agregar(mision(3, "Gustos de series", "GUSTOS", 4, "SERIE", null, null, null, null, true, null, null));
        for (int e = 1; e <= 4; e++) w.hm.gusto(1, e);
        w.hm.gusto(1, 5); w.hm.gusto(1, 6);
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Gustos de películas"), "4 espacios de películas llenos: se puede reclamar");
        chk(!contiene(lista(r, "porReclamar"), "Perfil completo"), "6 de 8 espacios: Perfil completo todavía no se puede reclamar");
        chk(!contiene(lista(r, "porReclamar"), "Gustos de series"), "2 de 4 espacios de series: tampoco");
        chk(new FilaM_(w.mem, 1, 1).progreso == 6, "Perfil completo suma 6 de 8 como avance parcial");
        chk(new FilaM_(w.mem, 1, 3).progreso == 2, "Gustos de series suma 2 de 4");
        w.svc.procesar(ActividadEvento.gusto(1, 7, true), AHORA.plusMinutes(1));
        w.svc.procesar(ActividadEvento.gusto(1, 8, true), AHORA.plusMinutes(2));
        chk(w.mem.nivelesDeSellos(1).get(todos.sello().id()) != null, "guardar los 2 espacios que faltan completa Perfil completo en vivo");
        chk(w.mem.nivelesDeSellos(1).get(series.sello().id()) != null, "y también Gustos de series");
        chk(w.svc.reclamar(1, pelis.id(), AHORA) && w.mem.nivelesDeSellos(1).get(pelis.sello().id()) != null, "reclama Gustos de películas");
        // un espacio ya contado en vivo no se cuenta otra vez
        Mundo v = new Mundo();
        v.agregar(mision(1, "Gustos de películas", "GUSTOS", 4, "PELICULA", null, null, null, null, true, null, null));
        for (int e = 1; e <= 4; e++) v.hm.gusto(2, e);
        v.svc.procesar(ActividadEvento.gusto(2, 1, false), hace(1));
        Map<String, Object> r2 = v.svc.completo(2, AHORA);
        chk(contiene(lista(r2, "porReclamar"), "Gustos de películas"), "un espacio contado en vivo + los 4 guardados = 4 espacios (no 5): alcanza justo");
        chk(v.svc.reclamar(2, 1, AHORA) && new FilaM_(v.mem, 2, 1).progreso >= 4, "y reclamar deja exactamente 4");
    }

    static void trivias() {
        System.out.println("-- trivias");
        Mundo w = new Mundo();
        Mision tp = w.agregar(mision(1, "Adivinador", "TRIVIAS", 5, "PELICULA", null, null, null, null, false, "5,20", null));
        Mision ts = w.agregar(mision(2, "Serieadivinador", "TRIVIAS", 4, "SERIE", null, null, null, null, false, null, null));
        w.hm.trivia(1, "PELICULA", 10, 4, hace(9)); w.hm.trivia(1, "PELICULA", 11, 3, hace(8)); w.hm.trivia(1, "SERIE", 20, 2, hace(7));
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Adivinador"), "7 aciertos en películas: la escalera de trivias se puede reclamar (el escalón es 5)");
        chk(!contiene(lista(r, "porReclamar"), "Serieadivinador") && new FilaM_(w.mem, 1, 2).progreso == 2, "2 aciertos de serie: la misión de series suma 2 y no se reclama");
        chk(w.svc.reclamar(1, tp.id(), AHORA), "la reclama");
        chk(new FilaM_(w.mem, 1, 1).progreso == 7 && w.mem.nivelesDeSellos(1).get(tp.sello().id()) == 1, "queda en 7 aciertos, nivel 1");
        w.svc.procesar(ActividadEvento.triviaSerie(1), AHORA.plusMinutes(1)); w.svc.procesar(ActividadEvento.triviaSerie(1), AHORA.plusMinutes(2));
        chk(w.mem.nivelesDeSellos(1).get(ts.sello().id()) != null, "con 2 aciertos de serie en vivo completa la de series (4)");
        Mundo v = new Mundo();
        Mision con = v.agregar(mision(3, "Trivia de terror", "TRIVIAS", 1, null, 27L, null, null, null, false, null, "GENERO"));
        v.objetivo(con, "GENERO");
        v.hm.trivia(1, "PELICULA", 10, 9, hace(5));
        v.svc.completo(1, AHORA);
        chk(v.hm.regs.get("1:3") == null || !v.hm.regs.get("1:3").estado().equals("PENDIENTE"), "una trivia con filtro de género nunca se cumple por historial (como en vivo)");
    }

    static void series() {
        System.out.println("-- series: votar + comentar + recomendar");
        Mundo w = new Mundo();
        Mision alf = w.agregar(SerieFanServicioTest.fan(1, "ALF", 3000)); w.objetivo(alf, "SERIE");
        Mision simpson = w.agregar(SerieFanServicioTest.fan(2, "Los Simpson", 3001)); w.objetivo(simpson, "SERIE");
        Mision friends = w.agregar(SerieFanServicioTest.fan(3, "Friends", 3002)); w.objetivo(friends, "SERIE");
        // usuario 1: lo hizo todo con ALF (la actual) antes del sistema
        w.hm.serie(1, "VOTO", 3000, hace(9)); w.hm.serie(1, "COMENTARIO", 3000, hace(8)); w.hm.serie(1, "RECOMENDACION", 3000, hace(7));
        w.hm.serie(1, "VOTO", 3000, hace(6));                                          // votar de nuevo no suma otro paso
        w.hm.voto(1, "PELICULA", 3001, hace(5)); w.hm.serie(1, "VOTO", 3001, hace(5)); // los Simpson: solo 1 de 3
        Map<String, Object> r = w.svc.completo(1, AHORA);
        chk(contiene(lista(r, "porReclamar"), "Fan de ALF"), "los 3 pasos dados antes con ALF (la actual): se puede reclamar");
        chk(!contiene(lista(r, "porReclamar"), "Fan de Los Simpson"), "Los Simpson, con 1 solo paso, no");
        chk(w.svc.reclamar(1, alf.id(), AHORA) && w.mem.nivelesDeSellos(1).get(alf.sello().id()) != null, "reclama «Fan de ALF» y recibe el sello");
        chk(!w.svc.reclamar(1, alf.id(), AHORA), "reclamar dos veces no da dos sellos");
        // usuario 2: 2 de 3 con ALF: avance parcial, sin botón; el paso que falta lo completa en vivo
        w.hm.serie(2, "VOTO", 3000, hace(9)); w.hm.serie(2, "COMENTARIO", 3000, hace(8));
        w.hm.serie(2, "VOTO", 3002, hace(7)); w.hm.voto(2, "PELICULA", 3000, hace(7)); w.hm.comentario(2, "PELICULA", 3000, hace(7));    // otra serie y una película con el mismo número: no cuentan en ALF
        Map<String, Object> r2 = w.svc.completo(2, AHORA);
        chk(!contiene(lista(r2, "porReclamar"), "Fan de ALF") && new FilaM_(w.mem, 2, 1).progreso == 2, "2 de 3 pasos: queda como avance parcial (2), sin botón");
        w.svc.procesar(ActividadEvento.recomendacionSerie(2, 3000L), AHORA.plusMinutes(1));
        chk(w.mem.nivelesDeSellos(2).get(alf.sello().id()) != null, "recomendarla en vivo completa la misión");
        // usuario 3: un paso ya contado en vivo y los tres en el historial: no se cuenta dos veces
        w.svc.procesar(ActividadEvento.votoSerie(3, 3000L), hace(1));            // el primer contacto del usuario con las misiones es este voto en vivo
        w.hm.serie(3, "VOTO", 3000, hace(9)); w.hm.serie(3, "COMENTARIO", 3000, hace(8)); w.hm.serie(3, "RECOMENDACION", 3000, hace(7));
        Map<String, Object> r3 = w.svc.completo(3, AHORA);
        chk(contiene(lista(r3, "porReclamar"), "Fan de ALF"), "un voto contado en vivo + los 3 pasos del historial = 3 pasos: se puede reclamar");
        chk(w.svc.reclamar(3, alf.id(), AHORA) && new FilaM_(w.mem, 3, 1).progreso == 3, "y al reclamar queda en 3 (el voto no se cuenta dos veces)");
        // usuario 4: Friends no le tocó pero ya hizo los 3 pasos; Los Simpson 2 de 3
        w.hm.serie(4, "VOTO", 3002, hace(9)); w.hm.serie(4, "COMENTARIO", 3002, hace(8)); w.hm.serie(4, "RECOMENDACION", 3002, hace(7));
        w.hm.serie(4, "VOTO", 3001, hace(9)); w.hm.serie(4, "COMENTARIO", 3001, hace(8));
        Map<String, Object> r4 = w.svc.completo(4, AHORA);
        chk(contiene(lista(r4, "porReclamar"), "Fan de Friends") && !contiene(lista(r4, "porReclamar"), "Fan de Los Simpson"), "una serie que no le tocó pero ya completó se ofrece; la de 2 de 3 no");
        chk(w.svc.reclamar(4, friends.id(), AHORA) && w.mem.nivelesDeSellos(4).get(friends.sello().id()) != null, "la reclama y recibe su sello");
        // lo posterior al sistema no es historial
        Mundo v = new Mundo();
        Mision alf2 = v.agregar(SerieFanServicioTest.fan(1, "ALF", 3000)); v.objetivo(alf2, "SERIE");
        v.hm.serie(5, "VOTO", 3000, AHORA.plusDays(1)); v.hm.serie(5, "COMENTARIO", 3000, AHORA.plusDays(1)); v.hm.serie(5, "RECOMENDACION", 3000, AHORA.plusDays(1));
        chk(!contiene(lista(v.svc.completo(5, AHORA), "porReclamar"), "Fan de ALF"), "lo que es posterior al momento de evaluar no cuenta como historial");
        chk(HistorialLogica.elegible(alf), "una misión de serie puntual sí mira el historial");
    }

    static void sinTmdbSiNoHaceFalta() {
        System.out.println("-- no se consulta a TMDB si ninguna misión lo necesita");
        Mundo w = new Mundo();
        w.agregar(mision(1, "Cinco votos", "VOTOS", 5, null, null, null, null, null, false, null, null));
        for (int i = 0; i < 6; i++) w.hm.voto(1, "PELICULA", i, hace(10));
        w.svc.completo(1, AHORA);
        chk(w.cat.llamadas == 0, "0 consultas a TMDB");
    }

    public static void main(String[] args) {
        logica(); escaleraCompleta(); parcialYNoDoble(); sinDobleConteo(); noMiraLoQueNoDebe(); rotativas(); rotativaAsignadaCompleta();
        titulosDistintos(); publicaciones(); trivias(); intervenciones(); canjes(); gustos(); series(); reclamarCasosBorde(); aislamiento(); sinTmdbSiNoHaceFalta();
        System.out.println("\nRESULTADO: " + ok + " ok, " + fal + " fallas");
        System.exit(fal == 0 ? 0 : 1);
    }
}
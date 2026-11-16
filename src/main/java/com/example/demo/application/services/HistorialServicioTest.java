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
        chk(!HistorialLogica.elegible(trivias), "una métrica que todavía no tiene historial (trivias) no");
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
        titulosDistintos(); reclamarCasosBorde(); aislamiento(); sinTmdbSiNoHaceFalta();
        System.out.println("\nRESULTADO: " + ok + " ok, " + fal + " fallas");
        System.exit(fal == 0 ? 0 : 1);
    }
}

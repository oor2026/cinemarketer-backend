package com.example.demo.application.services;

import com.example.demo.application.services.MisionServicioTest.Catalogo;
import com.example.demo.application.services.MisionServicioTest.Memoria;
import com.example.demo.domain.mision.*;
import com.example.demo.domain.mision.MisionLogica.*;

import java.time.*;
import java.util.*;

/** Misiones de series («Fan de ALF»): votar + comentar + recomendar ESA serie, en cualquier orden. Se corre con main, como las demás pruebas. */
public class SerieFanServicioTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 12, 0);
    static long sid = 500;

    static Mision fan(long id, String nombre, long serieId) {
        Sello s = new Sello(++sid, "Fan de " + nombre, "saga", "Votaste, comentaste y recomendaste " + nombre + ".");
        return new Mision(id, "Fan de " + nombre, "Votá, comentá y recomendá " + nombre + ".", "Votaste, comentaste y recomendaste " + nombre + ".", "saga", "VOTOS", 3, "pasos",
                "PERMANENTE", "SERIE", null, null, null, null, false, s, 1, 1.0, null, null, "VISIBLE", null, null, null, null, 500, null, null, "PROGRESO", "SERIE", "TODOS", serieId);
    }

    static class Mundo {
        Memoria mem = new Memoria(); Catalogo cat = new Catalogo(); MisionService svc = new MisionService(mem, cat); long seq = 0;
        Mundo() { svc.usarAzar(new Random(7)); }
        Mision serie(String nombre, long serieId) {
            Mision m = fan(++seq + 100, nombre, serieId); mem.misiones.add(m);
            Memoria.Obj o = new Memoria.Obj(); o.id = m.id(); o.misionId = m.id(); o.familia = "SERIE"; o.nombre = nombre; mem.objetivos.add(o);   // se agregan de la más a la menos popular
            return m;
        }
        int progreso(long u, Mision m) { for (var x : mem.filas) if (x.userId == u && x.misionId == m.id() && x.ciclo.equals("P")) return x.progreso; return 0; }
        boolean tieneSello(long u, Mision m) { return mem.nivelesDeSellos(u).get(m.sello().id()) != null; }
        Memoria.AsigM asig(long u, String estado) { return mem.asigs.stream().filter(a -> a.userId == u && a.familia.equals("SERIE") && a.estado.equals(estado)).findFirst().orElse(null); }
        void ev(ActividadEvento e, int min) { svc.procesar(e, AHORA.plusMinutes(min)); }
    }

    static void asignacion() {
        System.out.println("-- se ofrecen de la más popular a la menos popular");
        Mundo w = new Mundo();
        Mision alf = w.serie("ALF", 3000), simpson = w.serie("Los Simpson", 3001), friends = w.serie("Friends", 3002);
        w.svc.completo(1, AHORA); w.svc.completo(2, AHORA);
        chk(w.asig(1, "ACTUAL").misionId == alf.id() && w.asig(1, "SIGUIENTE").misionId == simpson.id(), "el usuario 1 recibe la más popular (ALF) y de siguiente la que le sigue");
        chk(w.asig(2, "ACTUAL").misionId == alf.id(), "el usuario 2 también empieza por la más popular");
        Map<String, Object> r = w.svc.completo(1, AHORA);
        List<?> enCurso = (List<?>) r.get("enCurso");
        chk(enCurso.stream().anyMatch(x -> "Fan de ALF".equals(((Map<?, ?>) x).get("titulo")) && "SERIE".equals(((Map<?, ?>) x).get("familia"))), "ALF aparece en curso con la familia SERIE");
    }

    static void pasos() {
        System.out.println("-- tres pasos distintos sobre ESA serie");
        Mundo w = new Mundo();
        Mision alf = w.serie("ALF", 3000); w.serie("Los Simpson", 3001);
        w.svc.completo(1, AHORA);
        w.ev(ActividadEvento.votoSerie(1, 3000L), 1);
        chk(w.progreso(1, alf) == 1, "votar ALF: 1 de 3");
        w.ev(ActividadEvento.votoSerie(1, 3000L), 2);
        chk(w.progreso(1, alf) == 1, "votar ALF otra vez no suma");
        w.ev(ActividadEvento.votoSerie(1, 3001L), 3);
        chk(w.progreso(1, alf) == 1, "votar otra serie (Los Simpson) no suma en ALF");
        w.ev(ActividadEvento.votoPelicula(1, 3000L), 4); w.ev(ActividadEvento.comentarioPelicula(1, 3000L), 5);
        chk(w.progreso(1, alf) == 1, "una PELÍCULA con el mismo número que la serie no suma");
        w.ev(ActividadEvento.publicacion(1), 6); w.ev(ActividadEvento.triviaSerie(1), 6);
        chk(w.progreso(1, alf) == 1, "publicar o una trivia de series no suma");
        w.ev(ActividadEvento.recomendacionSerie(1, 3000L), 7);
        chk(w.progreso(1, alf) == 2 && !w.tieneSello(1, alf), "recomendar ALF (sin haberla comentado todavía): 2 de 3, sin sello");
        w.ev(ActividadEvento.recomendacionSerie(1, 3000L), 8);
        chk(w.progreso(1, alf) == 2, "recomendarla otra vez no suma");
        w.ev(ActividadEvento.comentarioSerie(1, 3000L), 9);
        chk(w.tieneSello(1, alf), "comentarla completa los 3 pasos: sello «Fan de ALF»");
        chk(w.mem.cels.stream().anyMatch(c -> c.userId == 1 && c.misionId == alf.id() && c.tipo.equals("NIVEL")), "y deja su celebración");
        w.ev(ActividadEvento.votoSerie(1, 3000L), 10);
        chk(w.progreso(1, alf) == 3, "una vez ganada, seguir votando no la mueve");
    }

    static void rotacionYOrden() {
        System.out.println("-- al completar una serie pasa la siguiente");
        Mundo w = new Mundo();
        Mision alf = w.serie("ALF", 3000), simpson = w.serie("Los Simpson", 3001), friends = w.serie("Friends", 3002);
        w.svc.completo(1, AHORA);
        w.ev(ActividadEvento.recomendacionSerie(1, 3000L), 1); w.ev(ActividadEvento.comentarioSerie(1, 3000L), 2); w.ev(ActividadEvento.votoSerie(1, 3000L), 3);
        chk(w.tieneSello(1, alf), "completa ALF en otro orden (recomendar, comentar, votar)");
        w.svc.completo(1, AHORA.plusMinutes(5));
        chk(w.mem.asigs.stream().anyMatch(a -> a.userId == 1 && a.misionId == alf.id() && a.estado.equals("COMPLETADO")), "ALF queda COMPLETADO");
        chk(w.asig(1, "ACTUAL").misionId == simpson.id() && w.asig(1, "SIGUIENTE").misionId == friends.id(), "Los Simpson pasa a actual y Friends queda de siguiente");
        w.ev(ActividadEvento.votoSerie(1, 3002L), 6);
        chk(w.progreso(1, friends) == 0, "Friends todavía no está en juego: lo que haga ahí no cuenta hasta que sea la actual");
        w.ev(ActividadEvento.votoSerie(1, 3001L), 7);
        chk(w.progreso(1, simpson) == 1, "Los Simpson, que sí es la actual, avanza");
    }

    static void descarte() {
        System.out.println("-- descartar una serie");
        Mundo w = new Mundo();
        Mision alf = w.serie("ALF", 3000), simpson = w.serie("Los Simpson", 3001);
        w.svc.completo(1, AHORA);
        chk(w.svc.descartar(1, alf.id(), AHORA), "se puede descartar con la ✕");
        w.svc.completo(1, AHORA.plusMinutes(1));
        chk(w.asig(1, "ACTUAL") != null && w.asig(1, "ACTUAL").misionId == simpson.id(), "pasa Los Simpson");
    }

    static void logica() {
        System.out.println("-- reglas puras");
        Mision alf = fan(1, "ALF", 3000);
        chk(alf.esDeSerie() && alf.cuentaDistintos() && !alf.necesitaTmdb(), "es de serie, cuenta distintos y NO consulta a TMDB (no hace falta)");
        chk(MisionLogica.claveDistinta(alf, ActividadEvento.votoSerie(1, 3000L)) == 1L && MisionLogica.claveDistinta(alf, ActividadEvento.comentarioSerie(1, 3000L)) == 2L
                && MisionLogica.claveDistinta(alf, ActividadEvento.recomendacionSerie(1, 3000L)) == 3L, "voto = paso 1, comentario = paso 2, recomendación = paso 3");
        chk(MisionLogica.claveDistinta(alf, ActividadEvento.publicacion(1)) == null, "una publicación no es un paso");
        chk(!HistorialLogica.elegible(alf), "las misiones de serie todavía no miran el historial (no se regala nada con los votos de series en general)");
        Mision comun = new Mision(2, "Votador", "v", "v", "votos", "VOTOS", 5, "votos", "PERMANENTE", null, null, null, null, null, false, new Sello(900, "V", "votos", "v"), 1, 1.0, null, null, "VISIBLE", null, null, null, null, 1);
        chk(!comun.esDeSerie() && MisionLogica.claveDistinta(comun, ActividadEvento.votoPelicula(1, 77L)) == 77L, "una misión común sigue contando por el id de la película");
        chk(MisionLogica.FAMILIAS.contains("SERIE"), "SERIE es una de las familias que rotan");
        chk(RotacionTextos.titulo("SERIE", "ALF").equals("Fan de ALF") && RotacionTextos.categoria("SERIE").equals("saga") && RotacionTextos.META_SERIE == 3, "texto, categoría y meta de la familia");
    }

    public static void main(String[] args) {
        logica(); asignacion(); pasos(); rotacionYOrden(); descarte();
        System.out.println("\nRESULTADO: " + ok + " ok, " + fal + " fallas");
        if (fal > 0) System.exit(1);
    }
}

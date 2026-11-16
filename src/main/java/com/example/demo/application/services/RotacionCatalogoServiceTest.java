package com.example.demo.application.services;

import com.example.demo.domain.mision.CatalogoTmdb;
import com.example.demo.domain.mision.RotacionDatos;
import com.example.demo.domain.mision.RotacionTextos;

import java.util.*;

/** Prueba del catálogo automático: los criterios de qué entra, la idempotencia, los topes y qué pasa cuando algo falla. Sin red ni base. */
public class RotacionCatalogoServiceTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }

    // ---------------------------------------------------------------------------------------------- un TMDB falso
    static class Falso implements CatalogoTmdb {
        List<Genero> generos = new ArrayList<>(); List<PeliculaPopular> populares = new ArrayList<>();
        Map<Long, Long> sagaDe = new HashMap<>(); Map<Long, Saga> sagas = new HashMap<>();
        List<Persona> actores = new ArrayList<>(); Map<Long, List<Persona>> directores = new HashMap<>();
        Map<Long, Integer> comoActor = new HashMap<>(), comoDirector = new HashMap<>();
        Map<Long, String> fotos = new HashMap<>();
        Set<String> falla = new HashSet<>();          // «populares», «generos», «actores», «sagaDe:7»...
        List<SeriePopular> series = new ArrayList<>();
        int llamadas = 0;
        void ver(String k) { llamadas++; if (falla.contains(k)) throw new RuntimeException("TMDB no responde (" + k + ")"); }
        public List<SeriePopular> seriesPopulares(int p) { ver("series"); return series; }
        public List<Genero> generos() { ver("generos"); return generos; }
        public List<PeliculaPopular> peliculasPopulares(int p) { ver("populares"); return populares; }
        public Long sagaDe(long id) { ver("sagaDe:" + id); return sagaDe.get(id); }
        public Saga saga(long id) { ver("saga:" + id); return sagas.get(id); }
        public List<Persona> actoresPopulares(int p) { ver("actores"); return actores; }
        public List<Persona> directoresDe(long id) { ver("directoresDe:" + id); return directores.getOrDefault(id, List.of()); }
        public String fotoDe(long id) { ver("foto:" + id); return fotos.get(id); }
        public int peliculasComoActor(long id) { ver("comoActor:" + id); return comoActor.getOrDefault(id, 0); }
        public int peliculasComoDirector(long id) { ver("comoDirector:" + id); return comoDirector.getOrDefault(id, 0); }
    }

    // ---------------------------------------------------------------------------------------------- una base en memoria (decide igual que el repositorio real)
    static class Mem implements RotacionDatos {
        record Obj(String nombre, int meta, double pop, boolean activo, boolean excluido) {}
        Map<String, String> img = new HashMap<>();          // familia:id → imagen del sello
        Map<String, Obj> objs = new LinkedHashMap<>(); boolean ocupado = false; List<List<Objetivo>> recibidas = new ArrayList<>();
        static String k(String f, long id) { return f + ":" + id; }
        public Optional<Resultado> aplicar(List<Objetivo> lista) {
            recibidas.add(lista); if (ocupado) return Optional.empty();
            int c = 0, a = 0, s = 0, o = 0;
            for (Objetivo x : lista) {
                Obj e = objs.get(k(x.familia(), x.tmdbId()));
                if (e == null) { objs.put(k(x.familia(), x.tmdbId()), new Obj(x.nombre(), x.meta(), x.popularidad(), true, false)); if (x.imagenUrl() != null) img.put(k(x.familia(), x.tmdbId()), x.imagenUrl()); c++; }
                else if (e.excluido() || !e.activo()) o++;
                else if (e.meta() == x.meta() && e.nombre().equals(x.nombre())) s++;
                else { objs.put(k(x.familia(), x.tmdbId()), new Obj(x.nombre(), x.meta(), x.popularidad(), true, false)); a++; }
            }
            return Optional.of(new Resultado(c, a, s, o));
        }
        public int cantidadObjetivos() { return objs.size(); }
        public List<Long> personasSinFoto(String f) { List<Long> r = new ArrayList<>(); objs.forEach((k, v) -> { if (k.startsWith(f + ":") && v.activo() && !v.excluido() && !img.containsKey(k)) r.add(Long.parseLong(k.substring(f.length() + 1))); }); return r; }
        public void ponerFoto(String f, long id, String url) { img.putIfAbsent(k(f, id), url); }
        long de(String familia) { return objs.keySet().stream().filter(x -> x.startsWith(familia + ":")).count(); }
    }

    static RotacionCatalogoService svc(Falso f, Mem m) { return new RotacionCatalogoService(f, m, 0); }

    static Falso catalogo() {
        Falso f = new Falso();
        f.generos = List.of(new CatalogoTmdb.Genero(28, "Acción"), new CatalogoTmdb.Genero(27, "Terror"), new CatalogoTmdb.Genero(35, "Comedia"), new CatalogoTmdb.Genero(10770, "Película de TV"), new CatalogoTmdb.Genero(99, " "));
        for (long i = 1; i <= 9; i++) f.populares = concat(f.populares, new CatalogoTmdb.PeliculaPopular(i, 100 - i));
        f.sagaDe.put(1L, 100L); f.sagaDe.put(2L, 100L); f.sagaDe.put(3L, 100L); f.sagaDe.put(4L, 200L); f.sagaDe.put(5L, 300L); f.sagaDe.put(6L, 400L); f.sagaDe.put(7L, 400L);
        f.sagas.put(100L, new CatalogoTmdb.Saga(100, "Harry Potter", 8)); f.sagas.put(200L, new CatalogoTmdb.Saga(200, "El Señor de los Anillos", 3));
        f.sagas.put(300L, new CatalogoTmdb.Saga(300, "Una sola película", 1)); f.sagas.put(400L, new CatalogoTmdb.Saga(400, "Rápidos y Furiosos", 20));
        f.actores = List.of(new CatalogoTmdb.Persona(1, "Meryl Streep", 90), new CatalogoTmdb.Persona(2, "Actor nuevo", 80), new CatalogoTmdb.Persona(3, "Tom Hanks", 70), new CatalogoTmdb.Persona(4, " ", 60));
        f.comoActor.put(1L, 40); f.comoActor.put(2L, 3); f.comoActor.put(3L, 8); f.comoActor.put(4L, 50);
        f.directores.put(1L, List.of(new CatalogoTmdb.Persona(525, "Christopher Nolan", 0))); f.directores.put(2L, List.of(new CatalogoTmdb.Persona(525, "Christopher Nolan", 0)));
        f.directores.put(3L, List.of(new CatalogoTmdb.Persona(525, "Christopher Nolan", 0), new CatalogoTmdb.Persona(488, "Steven Spielberg", 0)));
        f.directores.put(4L, List.of(new CatalogoTmdb.Persona(488, "Steven Spielberg", 0))); f.directores.put(5L, List.of(new CatalogoTmdb.Persona(999, "Director novato", 0)));
        f.comoDirector.put(525L, 12); f.comoDirector.put(488L, 30); f.comoDirector.put(999L, 2);
        return f;
    }
    static <T> List<T> concat(List<T> a, T b) { List<T> r = new ArrayList<>(a); r.add(b); return r; }

    public static void main(String[] args) {
        System.out.println("=== 1) Textos y metas");
        chk(RotacionTextos.titulo("SAGA", "Harry Potter").equals("Saga Harry Potter") && RotacionTextos.titulo("ACTOR", "Meryl Streep").equals("Fan de Meryl Streep")
                && RotacionTextos.titulo("DIRECTOR", "Christopher Nolan").equals("Cine de Christopher Nolan") && RotacionTextos.titulo("GENERO", "Terror").equals("Maratón de terror"), "los 4 títulos: Saga X · Fan de X · Cine de X · Maratón de x (el género en minúscula)");
        chk(RotacionTextos.descripcion("SAGA", 8, "Harry Potter").equals("Votá las 8 películas de la saga Harry Potter.") && RotacionTextos.cumplida("ACTOR", 5, "Meryl Streep").equals("Votaste 5 películas con Meryl Streep."), "las descripciones llevan la meta de cada objetivo");
        chk(RotacionTextos.categoria("SAGA").equals("saga") && RotacionTextos.categoria("ACTOR").equals("elenco") && RotacionTextos.categoria("DIRECTOR").equals("autor") && RotacionTextos.categoria("GENERO").equals("genero"), "cada familia usa la categoría (color e ícono) que le corresponde");
        chk(RotacionTextos.META_ACTOR == 5 && RotacionTextos.META_DIRECTOR == 5 && RotacionTextos.META_GENERO == 10, "metas fijas: actor/actriz 5, director 5, género 10");

        System.out.println("=== 2) Primera corrida: qué entra y qué no");
        Falso f = catalogo(); Mem m = new Mem(); RotacionCatalogoService.Resumen r = svc(f, m).refrescar();
        chk(m.de("GENERO") == 3 && m.objs.get("GENERO:10770") == null && m.objs.get("GENERO:99") == null, "géneros: entran 3; «Película de TV» y el de nombre vacío no");
        chk(m.objs.get("GENERO:27").meta() == 10 && m.objs.get("GENERO:27").nombre().equals("Terror"), "el género tiene meta 10");
        chk(m.de("SAGA") == 3 && m.objs.get("SAGA:300") == null, "sagas: entran 3; la que tiene 1 sola película estrenada no");
        chk(m.objs.get("SAGA:100").meta() == 8 && m.objs.get("SAGA:200").meta() == 3, "la meta de cada saga es su cantidad de películas estrenadas (8 y 3)");
        chk(m.objs.get("SAGA:400").meta() == 20, "SIN TOPE: una saga de 20 películas pide 20");
        chk(m.objs.get("SAGA:100").nombre().equals("Harry Potter") && m.objs.get("SAGA:400").nombre().equals("Rápidos y Furiosos"), "el nombre es el de la saga armada de Cinemarketer");
        chk(m.de("ACTOR") == 2 && m.objs.get("ACTOR:2") == null && m.objs.get("ACTOR:4") == null, "actores: entran 2; el de 3 películas y el de nombre vacío no");
        chk(m.objs.get("ACTOR:3") != null && m.objs.get("ACTOR:1").meta() == 5, "el que tiene justo 8 películas entra (límite) y la meta es 5");
        chk(m.de("DIRECTOR") == 2 && m.objs.get("DIRECTOR:999") == null, "directores: entran 2; el que dirigió solo 2 no");
        chk(m.objs.get("DIRECTOR:525").meta() == 5 && m.objs.get("DIRECTOR:525").pop() == 3.0 && m.objs.get("DIRECTOR:488").pop() == 2.0, "meta 5; se guarda en cuántas populares aparece (3 y 2)");
        chk(r.errores().isEmpty() && r.porFamilia().size() == 5 && r.porFamilia().get("SAGA").creados() == 3, "el resumen cuenta lo creado y no trae errores");
        chk(r.texto().contains("SAGA: +3 nuevos"), "y se puede escribir en el registro: «" + r.texto().substring(0, 60) + "...»");

        System.out.println("=== 3) Idempotencia y cambios");
        RotacionCatalogoService.Resumen r2 = svc(f, m).refrescar(); int total = m.objs.size();
        chk(r2.porFamilia().values().stream().allMatch(x -> x.creados() == 0 && x.actualizados() == 0) && m.objs.size() == total, "correr de nuevo no crea ni cambia nada (sin duplicados)");
        f.sagas.put(100L, new CatalogoTmdb.Saga(100, "Harry Potter", 9));
        RotacionCatalogoService.Resumen r3 = svc(f, m).refrescar();
        chk(r3.porFamilia().get("SAGA").actualizados() == 1 && m.objs.get("SAGA:100").meta() == 9, "si la saga estrena otra película, se actualiza su meta (8 → 9)");

        System.out.println("=== 4) Lo que el admin sacó no se toca");
        m.objs.put("SAGA:100", new Mem.Obj("Harry Potter", 9, 1, true, true)); f.sagas.put(100L, new CatalogoTmdb.Saga(100, "Harry Potter", 10));
        RotacionCatalogoService.Resumen r4 = svc(f, m).refrescar();
        chk(r4.porFamilia().get("SAGA").omitidos() == 1 && m.objs.get("SAGA:100").meta() == 9, "un objetivo excluido queda omitido y no se actualiza");
        m.objs.put("ACTOR:1", new Mem.Obj("Meryl Streep", 5, 1, false, false));
        chk(svc(f, m).refrescar().porFamilia().get("ACTOR").omitidos() == 1 && !m.objs.get("ACTOR:1").activo(), "uno desactivado tampoco se reactiva");

        System.out.println("=== 5) El catálogo solo crece");
        Falso chico = new Falso(); chico.generos = List.of(new CatalogoTmdb.Genero(28, "Acción")); int antes = m.objs.size();
        svc(chico, m).refrescar();
        chk(m.objs.size() == antes, "si la lista popular de este mes es más chica, no se borra ni desactiva nada de lo anterior");

        System.out.println("=== 6) Topes por familia");
        Falso g = new Falso(); Mem mg = new Mem();
        for (long i = 1; i <= 80; i++) { g.populares = concat(g.populares, new CatalogoTmdb.PeliculaPopular(i, i)); g.sagaDe.put(i, 1000 + i); g.sagas.put(1000 + i, new CatalogoTmdb.Saga(1000 + i, "Saga " + i, 3));
            g.directores.put(i, List.of(new CatalogoTmdb.Persona(5000 + i, "Director " + i, 0))); g.comoDirector.put(5000 + i, 10); }
        for (long i = 1; i <= 90; i++) { g.actores = concat(g.actores, new CatalogoTmdb.Persona(i, "Actor " + i, 100 - i)); g.comoActor.put(i, 20); }
        svc(g, mg).refrescar();
        chk(mg.de("SAGA") == 0 && mg.de("ACTOR") == RotacionCatalogoService.MAX_ACTORES && mg.de("DIRECTOR") == RotacionCatalogoService.MAX_DIRECTORES, "tope por corrida: 60 actores y 40 directores aunque haya más candidatos; ninguna de las 80 colecciones populares (que no son sagas fijas) se cuela como saga");
        chk(mg.objs.containsKey("ACTOR:1") && !mg.objs.containsKey("ACTOR:90"), "de los actores se toman los primeros de la lista (los más populares)");

        System.out.println("=== 7) Si TMDB falla, el resto se completa igual");
        Falso h = catalogo(); h.falla.add("actores"); Mem mh = new Mem(); RotacionCatalogoService.Resumen rh = svc(h, mh).refrescar();
        chk(mh.de("ACTOR") == 0 && mh.de("GENERO") == 3 && mh.de("SAGA") == 3 && mh.de("DIRECTOR") == 2, "si falla la lista de actores, géneros, sagas y directores se arman igual");
        chk(rh.errores().size() == 1 && rh.errores().get(0).startsWith("ACTOR"), "y el resumen dice cuál falló: " + rh.errores());
        Falso h2 = catalogo(); h2.falla.add("populares"); Mem mh2 = new Mem(); RotacionCatalogoService.Resumen rh2 = svc(h2, mh2).refrescar();
        chk(mh2.de("GENERO") == 3 && mh2.de("ACTOR") == 2 && mh2.de("SAGA") == 3 && mh2.de("DIRECTOR") == 0 && rh2.errores().get(0).startsWith("películas populares"), "sin la lista de populares no hay directores, pero géneros, actores y sagas (que no dependen de ella) sí");
        Falso h3 = catalogo(); h3.falla.add("saga:200"); h3.falla.add("comoActor:1"); Mem mh3 = new Mem(); RotacionCatalogoService.Resumen rh3 = svc(h3, mh3).refrescar();
        chk(mh3.objs.containsKey("SAGA:100") && !mh3.objs.containsKey("SAGA:200") && !mh3.objs.containsKey("ACTOR:1") && mh3.objs.containsKey("ACTOR:3"), "un pedido suelto que falla (una saga, un actor) se saltea sin frenar a los demás");
        chk(rh3.errores().isEmpty(), "y eso no se cuenta como error de la corrida");

        System.out.println("=== 8) Otro proceso ya lo está corriendo");
        Mem mo = new Mem(); mo.ocupado = true; RotacionCatalogoService.Resumen ro = svc(catalogo(), mo).refrescar();
        chk(mo.objs.isEmpty() && ro.errores().size() == 5 && ro.errores().stream().allMatch(x -> x.contains("otro proceso")), "si la base dice que ya hay otra corrida, no se hace nada y se avisa en las 5 familias");

        System.out.println("=== 9) Repetidos y casos raros");
        Falso fx = catalogo(); fx.sagas.put(777L, new CatalogoTmdb.Saga(777, "El Hombre Araña", 5)); fx.sagaDe.put(9L, 777L); Mem mfx = new Mem(); svc(fx, mfx).refrescar();
        chk(!mfx.objs.containsKey("SAGA:777") && mfx.de("SAGA") == 3, "una colección popular de TMDB que NO es una de las sagas armadas (p. ej. El Hombre Araña) no genera misión");
        Falso im = catalogo(); im.sagas.put(100L, new CatalogoTmdb.Saga(100, "Harry Potter", 8, "https://image.tmdb.org/t/p/w342/hp.jpg", "https://image.tmdb.org/t/p/w780/f.jpg")); Mem mim = new Mem(); svc(im, mim).refrescar();
        RotacionDatos.Objetivo oim = mim.recibidas.stream().flatMap(List::stream).filter(x -> x.familia().equals("SAGA") && x.tmdbId() == 100).findFirst().orElse(null);
        chk(oim != null && oim.imagenUrl().endsWith("/hp.jpg") && oim.fondoUrl().endsWith("/f.jpg"), "el objetivo de la saga lleva el póster y el fondo de su colección");
        chk(mim.recibidas.stream().flatMap(List::stream).filter(x -> x.familia().equals("SAGA") && x.tmdbId() == 200).allMatch(x -> x.imagenUrl() == null && x.fondoUrl() == null), "la saga sin imagen en TMDB va sin imagen");
        Falso fo = catalogo(); fo.actores = List.of(new CatalogoTmdb.Persona(1, "Meryl Streep", 90, "https://image.tmdb.org/t/p/w185/meryl.jpg"), new CatalogoTmdb.Persona(3, "Tom Hanks", 70));
        fo.directores.put(1L, List.of(new CatalogoTmdb.Persona(525, "Christopher Nolan", 0, "https://image.tmdb.org/t/p/w185/nolan.jpg")));
        Mem mfo = new Mem(); svc(fo, mfo).refrescar();
        chk("https://image.tmdb.org/t/p/w185/meryl.jpg".equals(mfo.img.get("ACTOR:1")) && "https://image.tmdb.org/t/p/w185/nolan.jpg".equals(mfo.img.get("DIRECTOR:525")), "el actor y el director llevan su foto de TMDB en el sello");
        chk(!mfo.img.containsKey("ACTOR:3"), "el que no tiene foto en TMDB queda sin imagen (ícono de su categoría) y no rompe nada");
        Falso f2 = catalogo(); f2.fotos.put(5064L, "https://image.tmdb.org/t/p/w185/seed.jpg"); f2.fotos.put(77L, "https://image.tmdb.org/t/p/w185/x.jpg"); f2.falla.add("foto:88");
        Mem m2 = new Mem(); m2.objs.put("ACTOR:5064", new Mem.Obj("Meryl Streep", 5, 0, true, false)); m2.objs.put("ACTOR:88", new Mem.Obj("Falla", 5, 0, true, false)); m2.objs.put("ACTOR:77", new Mem.Obj("Excluido", 5, 0, true, true)); m2.objs.put("DIRECTOR:99", new Mem.Obj("Sin foto en TMDB", 5, 0, true, false));
        RotacionCatalogoService.Resumen rFoto = svc(f2, m2).refrescar();
        chk("https://image.tmdb.org/t/p/w185/seed.jpg".equals(m2.img.get("ACTOR:5064")), "un actor que ya estaba en el catálogo y NO figura entre los populares de este mes recibe su foto pidiéndola directo");
        chk(!m2.img.containsKey("ACTOR:88") && !m2.img.containsKey("ACTOR:77") && !m2.img.containsKey("DIRECTOR:99"), "si el pedido falla, si no tiene foto o si el admin lo excluyó, no se le pone nada");
        chk(rFoto.errores().isEmpty(), "y un pedido de foto que falla no cuenta como error de la corrida");
        Falso rs = catalogo(); Mem mrs = new Mem(); RotacionCatalogoService.Resumen rrs = svc(rs, mrs).refrescarSagas();
        chk(mrs.de("SAGA") == 3 && mrs.de("GENERO") == 0 && mrs.de("ACTOR") == 0 && mrs.de("DIRECTOR") == 0 && rrs.porFamilia().size() == 1, "refrescarSagas trae solo las sagas (para correrlo en cada arranque) y no toca las otras familias");
        chk(svc(rs, mrs).refrescarSagas().porFamilia().get("SAGA").sinCambios() == 3 && svc(rs, mrs).refrescarSagas().porFamilia().get("SAGA").creados() == 0, "correrlo de nuevo no duplica nada: las 3 quedan «sin cambios»");
        Falso d = catalogo(); d.populares = concat(d.populares, new CatalogoTmdb.PeliculaPopular(8, 5)); Mem md = new Mem(); svc(d, md).refrescar();
        chk(md.de("SAGA") == 3, "las sagas no dependen de qué películas sean populares: siguen siendo las 3 fijas que cumplen");
        Falso e = catalogo(); e.sagas.put(100L, new CatalogoTmdb.Saga(100, "X", 2)); Mem me = new Mem(); svc(e, me).refrescar();
        chk(me.objs.containsKey("SAGA:100") && me.objs.get("SAGA:100").meta() == 2, "una saga con justo 2 estrenadas entra (límite) y pide 2");
        chk(new Mem().cantidadObjetivos() == 0 && svc(catalogo(), m).cantidadObjetivos() == m.objs.size(), "cantidadObjetivos refleja lo que hay en la base");

        System.out.println("=== 10) Nombres largos o desprolijos");
        String largo = "Colección de un nombre larguísimo ".repeat(6).trim();
        chk(RotacionTextos.nombre("  Harry   Potter \n ").equals("Harry Potter"), "se sacan los espacios de más");
        chk(RotacionTextos.nombre(largo).length() == RotacionTextos.MAX_NOMBRE && RotacionTextos.nombre(largo).endsWith("…"), "un nombre de " + largo.length() + " caracteres se recorta a " + RotacionTextos.MAX_NOMBRE + " con «…»");
        chk(RotacionTextos.recortar(RotacionTextos.titulo("SAGA", RotacionTextos.nombre(largo)), RotacionTextos.MAX_TITULO).length() <= RotacionTextos.MAX_TITULO, "y el título del sello nunca pasa de " + RotacionTextos.MAX_TITULO + " (el límite de la base es 80)");
        Falso lg = catalogo(); lg.sagas.put(100L, new CatalogoTmdb.Saga(100, largo, 8)); Mem ml = new Mem(); svc(lg, ml).refrescar();
        chk(ml.objs.get("SAGA:100").nombre().length() <= RotacionTextos.MAX_NOMBRE, "el catálogo guarda el nombre ya recortado");
        chk(svc(lg, ml).refrescar().porFamilia().get("SAGA").actualizados() == 0, "y en la corrida siguiente NO figura como «actualizado» (el recorte es estable)");

        System.out.println("=== 11) Nombres de colecciones como los manda TMDB");
        chk(RotacionTextos.nombreSaga("Harry Potter - Colección").equals("Harry Potter") && RotacionTextos.nombreSaga("Toy Story Collection").equals("Toy Story") && RotacionTextos.nombreSaga("Matrix: Saga").equals("Matrix"), "se saca «- Colección», «Collection» y «: Saga» del final");
        chk(RotacionTextos.nombreSaga("Colección Rápidos y Furiosos").equals("Rápidos y Furiosos") && RotacionTextos.nombreSaga("Saga de la Guerra de las Galaxias").equals("Guerra de las Galaxias"), "y «Colección» / «Saga de la» del principio");
        chk(RotacionTextos.nombreSaga("Jurassic Park").equals("Jurassic Park") && RotacionTextos.nombreSaga("Pirates of the Caribbean Collection").equals("Pirates of the Caribbean") && RotacionTextos.nombreSaga("Saga").equals("Saga") && RotacionTextos.nombreSaga("La Saga").equals("La Saga"), "un nombre sin la palabra queda igual, y si al sacarla no quedara nada razonable se deja el original");
        Falso co = catalogo(); co.sagas.put(100L, new CatalogoTmdb.Saga(100, "Harry Potter - Colección", 8)); Mem mc = new Mem(); mc.objs.put("SAGA:100", new Mem.Obj("Harry Potter", 8, 1, true, false));
        RotacionCatalogoService.Resumen rc = svc(co, mc).refrescar();
        chk(rc.porFamilia().get("SAGA").actualizados() == 0 && mc.objs.get("SAGA:100").nombre().equals("Harry Potter"), "el objetivo de ejemplo «Harry Potter» coincide con «Harry Potter - Colección» de TMDB: no se duplica ni se renombra");


        System.out.println("=== SERIES) Las series más populares, con meta 3");
        Falso fs = catalogo(); Mem ms = new Mem();
        fs.series.add(new CatalogoTmdb.SeriePopular(3000, "ALF", 90.5, "https://img/alf.jpg", "https://img/alf-fondo.jpg"));
        fs.series.add(new CatalogoTmdb.SeriePopular(3001, "  Los   Simpson ", 80.0, null, null));
        fs.series.add(new CatalogoTmdb.SeriePopular(3000, "ALF (repetida)", 70.0, null, null));
        fs.series.add(new CatalogoTmdb.SeriePopular(3002, "", 60.0, null, null));
        RotacionCatalogoService.Resumen rs2 = svc(fs, ms).refrescarSeries();
        chk(ms.de("SERIE") == 2 && rs2.porFamilia().size() == 1 && ms.de("GENERO") == 0 && ms.de("SAGA") == 0, "refrescarSeries trae solo las series (2: sin la repetida ni la de nombre vacío) y no toca las otras familias");
        chk(ms.objs.get("SERIE:3000").meta() == 3 && ms.objs.get("SERIE:3000").pop() == 90.5 && ms.objs.get("SERIE:3001").nombre().equals("Los Simpson"), "meta 3, se guarda la popularidad y el nombre queda prolijo");
        chk(svc(fs, ms).refrescarSeries().porFamilia().get("SERIE").creados() == 0, "correrlo de nuevo no duplica nada");
        Falso fz = catalogo(); Mem mz = new Mem(); fz.series.add(new CatalogoTmdb.SeriePopular(5, "Friends", 10, null, null));
        chk(svc(fz, mz).refrescar().porFamilia().get("SERIE").creados() == 1, "el refresco completo también arma las series");
        Falso fy = catalogo(); Mem my = new Mem(); fy.falla.add("series");
        RotacionCatalogoService.Resumen rf = svc(fy, my).refrescar();
        chk(rf.errores().size() == 1 && rf.errores().get(0).startsWith("SERIE") && my.de("SAGA") == 3, "si TMDB no responde las series, falla solo esa familia: las otras se arman igual");
        System.out.println("\n" + ok + " verificaciones correctas, " + fal + " fallas");
        if (fal > 0) System.exit(1);
    }
}

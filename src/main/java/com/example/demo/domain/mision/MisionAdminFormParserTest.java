package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionAdminFormParser.Parseo;

import java.time.LocalDateTime;
import java.util.*;

public class MisionAdminFormParserTest {
    static int ok = 0, fal = 0;
    static void chk(boolean c, String m) { if (c) { ok++; System.out.println("  ok: " + m); } else { fal++; System.out.println("  FALLA: " + m); } }
    static Map<String, Object> m(Object... kv) { Map<String, Object> r = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]); return r; }

    public static void main(String[] args) {
        System.out.println("=== lo que manda el panel, tal cual");
        Parseo p = MisionAdminFormParser.parsear(m("metrica", "votos", "tipoContenido", "pelicula", "generoId", 27, "meta", 10, "vigencia", "permanente", "titulo", "  Maratón   de terror ", "descripcion", "Votá 10 películas de terror.",
                "audiencia", "todos", "dificultad", "media", "orden", 5, "visibilidad", "visible", "selloNombre", "Sello X", "selloCategoria", "GENERO", "distintas", false));
        MisionAdminForm f = p.form();
        chk(p.ok() && "VOTOS".equals(f.metrica()) && "PELICULA".equals(f.tipoContenido()) && "PERMANENTE".equals(f.vigencia()) && "TODOS".equals(f.audiencia()) && "MEDIA".equals(f.dificultad()) && "VISIBLE".equals(f.visibilidad()), "los códigos se pasan a mayúsculas (votos → VOTOS)");
        chk("Maratón de terror".equals(f.titulo()) && "genero".equals(f.selloCategoria()), "el título se limpia de espacios de más y la categoría va en minúscula");
        chk(f.generoId() == 27L && f.meta() == 10 && f.orden() == 5 && Boolean.FALSE.equals(f.distintas()), "números y booleanos");

        System.out.println("=== tolerante con el formato");
        Parseo q = MisionAdminFormParser.parsear(m("meta", "25", "generoId", "27", "orden", " 7 ", "distintas", "true", "escaleraPaso", 50.0, "sellosNecesarios", "3"));
        chk(q.ok() && q.form().meta() == 25 && q.form().generoId() == 27L && q.form().orden() == 7 && Boolean.TRUE.equals(q.form().distintas()) && q.form().escaleraPaso() == 50 && q.form().sellosNecesarios() == 3, "números como texto («25», « 7 »), un decimal entero (50.0) y «true» como texto");
        chk(MisionAdminFormParser.parsear(m("escalera", List.of(10, 25, "50"))).form().escalera().equals(List.of(10, 25, 50)) && MisionAdminFormParser.parsear(m("escalera", "10, 25 ,50")).form().escalera().equals(List.of(10, 25, 50)), "la escalera como lista o como texto con comas");
        chk(MisionAdminFormParser.parsear(m("escalera", "")).form().escalera() == null && MisionAdminFormParser.parsear(m("escalera", List.of())).form().escalera() == null, "una escalera vacía es «sin escalera» (null)");
        chk(MisionAdminFormParser.parsear(m("titulo", "", "descripcion", "   ", "accionTexto", null)).form().titulo() == null && MisionAdminFormParser.parsear(m("titulo", "", "descripcion", "   ")).form().descripcion() == null, "un texto vacío o en blanco es «no completado» (null)");

        System.out.println("=== fechas");
        Parseo d1 = MisionAdminFormParser.parsear(m("desde", "2026-12-01", "hasta", "2026-12-31"));
        chk(d1.ok() && d1.form().desde().equals(LocalDateTime.of(2026, 12, 1, 0, 0)) && d1.form().hasta().equals(LocalDateTime.of(2026, 12, 31, 23, 59, 59)), "sin hora: «desde» es el principio del día y «hasta» el final (el último día entra completo)");
        Parseo d2 = MisionAdminFormParser.parsear(m("desde", "2026-12-01T20:30", "hasta", "2026-12-24 18:00"));
        chk(d2.ok() && d2.form().desde().equals(LocalDateTime.of(2026, 12, 1, 20, 30)) && d2.form().hasta().equals(LocalDateTime.of(2026, 12, 24, 18, 0)), "con hora, con «T» o con espacio");

        System.out.println("=== el botón y las pantallas");
        chk("feed-films".equals(MisionAdminFormParser.parsear(m("accionDestino", "#feed-films")).form().accionDestino()) && "cartelera".equals(MisionAdminFormParser.parsear(m("accionDestino", "cartelera")).form().accionDestino()) && MisionAdminFormParser.parsear(m("accionDestino", "#")).form().accionDestino() == null, "el destino se guarda sin el «#», venga con él o sin él");
        String url = "https://res.cloudinary.com/demo/image/upload/v1/misiones/sellos/Ab_Cd.png";
        chk(url.equals(MisionAdminFormParser.parsear(m("selloImagenUrl", "  " + url + "  ")).form().selloImagenUrl()), "la URL de la imagen no se altera (mayúsculas y guiones bajos intactos)");

        System.out.println("=== lo que no se puede leer vuelve como problema, no como excepción");
        Parseo e = MisionAdminFormParser.parsear(m("meta", "diez", "generoId", 2.5, "escalera", "10,a,50", "desde", "mañana", "orden", 99999999999L, "titulo", "Se lee igual"));
        Set<String> campos = new HashSet<>(); e.problemas().forEach(x -> campos.add(x.campo()));
        chk(!e.ok() && campos.equals(Set.of("meta", "generoId", "escalera", "desde", "orden")), "cada campo ilegible se señala por su nombre: " + campos);
        chk("Se lee igual".equals(e.form().titulo()) && e.form().meta() == null && e.form().escalera() == null, "y lo que sí se podía leer se conserva (el resto queda vacío)");
        chk(MisionAdminFormParser.parsear(null).ok() && MisionAdminFormParser.parsear(Map.of()).form().titulo() == null, "sin datos (null o vacío) devuelve un formulario vacío y sin problemas: el validador dirá qué falta");
        chk(MisionAdminFormParser.parsear(m("meta", Double.NaN, "orden", Double.POSITIVE_INFINITY)).problemas().size() == 2, "NaN e infinito no pasan como números");

        System.out.println("\n" + ok + " verificaciones correctas, " + fal + " fallas");
        if (fal > 0) System.exit(1);
    }
}

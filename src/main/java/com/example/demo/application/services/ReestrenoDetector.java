package com.example.demo.application.services;

import com.example.demo.application.dtos.external.tmdb.TmdbReleaseDatesDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decide si una película es un REESTRENO en Argentina, a partir de sus fechas de estreno por país en TMDb.
 *
 * TMDb no tiene una marca de "reestreno": el reestreno se carga como una fecha de cine más (tipo 2 o 3) dentro del
 * país, con una nota de texto libre ("Reestreno", "Re-release", "20th Anniversary"...). La fecha principal de la
 * película (release_date) no cambia, por eso un reestreno nunca aparece como estreno mirando solo esa fecha.
 *
 * Regla: una fecha de cine en Argentina es un reestreno si
 *   (a) hay otra fecha de cine en Argentina al menos un año anterior (ya se había estrenado acá), o
 *   (b) su nota dice que es un reestreno Y la película se había estrenado al menos un año antes en cualquier país.
 * Sin una de las dos no se la marca: una película que estrenó en un festival o en otro país hace más de un año y
 * recién llega acá NO es un reestreno.
 *
 * Las fechas se leen como fecha de calendario ("2026-10-17"), sin convertirlas de zona horaria: el 17 sigue siendo 17.
 */
public final class ReestrenoDetector {

    public static final String PAIS = "AR";
    public static final int DIAS_ATRAS = 30;       // un reestreno que empezó hace hasta 30 días todavía se muestra (está en cines)
    public static final int DIAS_ADELANTE = 90;    // y se anuncian los de los próximos 90 días
    public static final int MARGEN_DIAS = 365;     // cuánto antes tuvo que estrenarse la película para que sea "reestreno"

    // \b al principio: "Preestreno" (función previa de un estreno nuevo) NO es un reestreno.
    private static final Pattern NOTA_DE_REESTRENO = Pattern.compile(
            "(?iu)\\bre-?estreno|\\bre-?release|relan[cç]amento|aniversario|anniversary|restaur|remaster");

    private ReestrenoDetector() {}

    /** El reestreno detectado: cuándo empieza en cines y qué dice la nota de TMDb. */
    public record Reestreno(LocalDate fecha, String nota) {}

    private record Entrada(LocalDate fecha, String nota) {}

    public static Optional<Reestreno> detectar(TmdbReleaseDatesDto fechas, String fechaPrincipal, LocalDate hoy) {
        List<Entrada> cines = entradasDeCineEnArgentina(fechas);
        if (cines.isEmpty()) return Optional.empty();

        LocalDate principal = leerFecha(fechaPrincipal);
        LocalDate desde = hoy.minusDays(DIAS_ATRAS);
        LocalDate hasta = hoy.plusDays(DIAS_ADELANTE);

        List<Entrada> reestrenos = new ArrayList<>();
        for (Entrada e : cines) {
            if (e.fecha().isBefore(desde) || e.fecha().isAfter(hasta)) continue;

            boolean yaSeEstrenoAca = cines.stream()
                    .anyMatch(otra -> otra != e && !otra.fecha().isAfter(e.fecha().minusDays(MARGEN_DIAS)));

            LocalDate masAntigua = cines.stream().map(Entrada::fecha).min(LocalDate::compareTo).get();
            if (principal != null && principal.isBefore(masAntigua)) masAntigua = principal;
            boolean notaYAntigua = NOTA_DE_REESTRENO.matcher(e.nota()).find()
                    && !masAntigua.isAfter(e.fecha().minusDays(MARGEN_DIAS));

            if (yaSeEstrenoAca || notaYAntigua) reestrenos.add(e);
        }
        if (reestrenos.isEmpty()) return Optional.empty();

        // Si hay uno por venir, el más próximo; si ya empezaron todos, el más reciente.
        Entrada elegido = reestrenos.stream().filter(e -> !e.fecha().isBefore(hoy)).min((a, b) -> a.fecha().compareTo(b.fecha()))
                .orElseGet(() -> reestrenos.stream().max((a, b) -> a.fecha().compareTo(b.fecha())).get());
        return Optional.of(new Reestreno(elegido.fecha(), elegido.nota()));
    }

    private static List<Entrada> entradasDeCineEnArgentina(TmdbReleaseDatesDto dto) {
        List<Entrada> salida = new ArrayList<>();
        if (dto == null || dto.getResults() == null) return salida;
        for (TmdbReleaseDatesDto.Pais pais : dto.getResults()) {
            if (pais == null || !PAIS.equals(pais.getCodigoPais()) || pais.getFechas() == null) continue;
            for (TmdbReleaseDatesDto.Fecha f : pais.getFechas()) {
                if (f == null || f.getType() == null || (f.getType() != 2 && f.getType() != 3)) continue;   // solo cine
                LocalDate fecha = leerFecha(f.getFecha());
                if (fecha != null) salida.add(new Entrada(fecha, f.getNote() == null ? "" : f.getNote().trim()));
            }
        }
        return salida;
    }

    /** "2026-10-17T00:00:00.000Z" → 2026-10-17. Se toman solo los 10 primeros caracteres: sin zona horaria de por medio. */
    static LocalDate leerFecha(String texto) {
        if (texto == null || texto.length() < 10) return null;
        try {
            return LocalDate.parse(texto.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
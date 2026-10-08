package com.example.demo.application.services;

import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.example.demo.domain.cinema.CinemaManualPrice;
import com.example.demo.domain.cinema.CinemaManualPriceRepository;
import com.example.demo.domain.cinema.Holiday;
import com.example.demo.domain.cinema.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Precios de referencia por cine y formato — sacados de las páginas
 * oficiales de precios de cada cadena (no de la fuente de cartelera, que
 * no expone precio). Cache en memoria, sin persistencia en base todavía
 * (mismo criterio que el resto de Cartelera), actualizado una vez por
 * día por CarteleraPreciosScheduler.
 *
 * IMPORTANTE — nivel de confianza de cada parser:
 * - Atlas, Cinemark, Santa Rosa, Showcase, Cinemacenter, Cinépolis, Cinema
 *   La Plata: ALTA — contrastados contra la página oficial real (octubre
 *   2026). Showcase lee un archivo JS estático (cron/data.js), no texto
 *   libre.
 * - Multiplex, Tadicor: ALTA — se vieron tablas HTML reales, se parsean
 *   como tablas de verdad (Jsoup), no a ciegas.
 * - Play Cinema: MEJOR ESFUERZO — construido con lo visto en fetches
 *   anteriores, sin HTML crudo en mano. Si devuelve vacío al probarlo en
 *   real, hay que revisar contra el HTML real y ajustar el regex/selector.
 * - Cinépolis: a veces responde 403 al scraper (pasó el 03/10 desde un
 *   equipo local y no se repitió al día siguiente). Si pasa, el log avisa
 *   "No se pudo actualizar precios de Cinépolis".
 *
 * cachePrecios está escopado por CADENA (cadena → sucursal → formato →
 * precio), no por sucursal plana: dos cadenas pueden tener una sucursal
 * con el mismo nombre corto (ej. "Quilmes" existe en Cinemark Y en
 * Showcase) — sin este namespace, la segunda en correr pisaba
 * silenciosamente el precio de la primera.
 *
 * El precio de cachePrecios es el REGULAR de HOY. Alrededor de él, y todo
 * leído de las propias páginas:
 * - cacheTarifasReducidas: tarifas reducidas por día u horario (lunes a
 *   miércoles, matinee, 2x1 de jueves a domingo antes de las 18 hs, valor
 *   especial solo web...), con la regla tal como la publica cada cadena.
 * - cachePreciosProgramados: aumentos ya anunciados con fecha de vigencia
 *   (Cinemark), para que una función en una fecha posterior vea el precio
 *   que ya rige ese día.
 * - cachePreciosDetallados: precio por categoría (menores, mayores, etc.).
 * - preciosManuales / reducidasManuales: precios cargados a mano en la base
 *   (tabla cinema_manual_prices) para cines sin fuente scrapeable.
 * - feriados (tabla holidays): para las tarifas que no valen en feriado.
 * obtenerPrecioDelDia() junta todo eso cuando se conoce la fecha y la hora
 * de la función.
 */
@Service
public class CarteleraPreciosService {

    private static final Logger log = LoggerFactory.getLogger(CarteleraPreciosService.class);
    private static final String UA = "Mozilla/5.0 (compatible; CinemarketerBot/1.0)";

    // Cadenas que reconocemos por el nombre del cine, incluso las que hoy
    // no devuelven datos (ej. Cinépolis bloquea al scraper). Mismos nombres
    // que usa actualizarPrecios(): si se suma una cadena allá, sumarla acá.
    private static final List<String> CADENAS_CONOCIDAS = List.of(
            "Cinemark", "Atlas", "Cinemacenter", "Multiplex", "Play Cinema", "Tadicor",
            "Santa Rosa", "Showcase", "Cinépolis", "Cinema Devoto", "Gran Rex",
            "Cines del Solar", "Cinema Adrogué", "Cinema La Plata", "Cines Paseo Aldrey");

    // Cines de nuestro directorio (agendadecine.com) cuyo nombre no contiene
    // la clave con que figuran en la fuente de precios. Clave: nombre del
    // directorio, sin acentos y en minúscula. Valor: {cadena, sucursal} tal
    // como están en cachePrecios. Es la ÚNICA vía por la que un cine sin
    // cadena reconocible en el nombre recibe precio. Si agendadecine
    // renombra un cine, su entrada acá deja de aplicar sin avisar:
    // /cobertura-precios lo muestra.
    private static final Map<String, String[]> EQUIVALENCIAS_DIRECTORIO = Map.ofEntries(
            Map.entry("cinemark salta", new String[]{"Cinemark", "Salta Alto NOA"}),
            Map.entry("cinemark hiper libertad", new String[]{"Cinemark", "Salta Hiper Libertad"}),
            Map.entry("showcase cordoba", new String[]{"Showcase", "Córdoba Villa Cabrera"}),
            Map.entry("showcase norte", new String[]{"Showcase", "Norcenter"}),
            Map.entry("imax theater", new String[]{"Showcase", "IMAX Theatre (Norcenter)"}),
            Map.entry("cine amadeus", new String[]{"Santa Rosa", "Amadeus"}),
            Map.entry("cine milenium", new String[]{"Santa Rosa", "Milenium"}),
            Map.entry("cinemacenter avalos", new String[]{"Cinemacenter", "Avenida Avalos"}),
            Map.entry("cinemacenter resistencia", new String[]{"Cinemacenter", "Libertad"}),
            Map.entry("cinemacenter altos del solar", new String[]{"Cinemacenter", "Alto del Solar"}),
            Map.entry("cine ambassador", new String[]{"Cinemacenter", "Ambassador"}),
            Map.entry("cinema los gallegos", new String[]{"Cinemacenter", "Cinema Los Gallegos"}),
            Map.entry("cine teatro atlantico", new String[]{"Cinemacenter", "Cine Teatro Atlantico"}),
            Map.entry("cines del paseo rio cuarto", new String[]{"Cinemacenter", "Paseo Río Cuarto"}),
            Map.entry("cinema city", new String[]{"Cinema La Plata", "Cinema City"}),
            Map.entry("cinema paradiso", new String[]{"Cinema La Plata", "Cinema Paradiso"}),
            Map.entry("cinema ocho", new String[]{"Cinema La Plata", "Cinema Ocho"}),
            Map.entry("cinema rocha", new String[]{"Cinema La Plata", "Cinema Rocha"}),
            Map.entry("cinema san martin", new String[]{"Cinema La Plata", "Cinema San Martin"}),
            Map.entry("cinepolis plaza houssay", new String[]{"Cinépolis", "Houssay - Facultad de Medicina"}));

    private final CarteleraArgentinaPreciosService carteleraArgentinaPreciosService;
    private final CinemaManualPriceRepository cinemaManualPriceRepository;
    private final HolidayRepository holidayRepository;

    public CarteleraPreciosService(CarteleraArgentinaPreciosService carteleraArgentinaPreciosService,
                                   CinemaManualPriceRepository cinemaManualPriceRepository,
                                   HolidayRepository holidayRepository) {
        this.carteleraArgentinaPreciosService = carteleraArgentinaPreciosService;
        this.cinemaManualPriceRepository = cinemaManualPriceRepository;
        this.holidayRepository = holidayRepository;
    }

    // cadena → sucursal (nombre corto, tal cual aparece en la fuente de
    // precios) → formato → precio. El match contra nuestros propios
    // nombres de cine se resuelve en resolver(), por "contiene".
    private Map<String, Map<String, Map<String, Double>>> cachePrecios = new HashMap<>();

    // Detalle por categoría (general, menores, mayores, matinee, promos...) de
    // las cadenas que lo publican: cadena → sucursal → formato → categoría →
    // precio. Las categorías salen de la etiqueta de cada fila de la página
    // (ver categoriaDesdeEtiqueta), no de una lista fija. Hoy lo completan
    // Showcase, Atlas, Cinemark y Santa Rosa. No cambia el precio de
    // referencia: es información para que el front decida qué mostrar.
    private Map<String, Map<String, Map<String, Map<String, Double>>>> cachePreciosDetallados = new HashMap<>();
    // Mientras corre actualizarPrecios() los scrapers cargan acá; recién al
    // terminar pasa a cachePreciosDetallados (mismo reemplazo que cachePrecios).
    private Map<String, Map<String, Map<String, Map<String, Double>>>> detalleEnCurso = new HashMap<>();

    public Map<String, Map<String, Map<String, Map<String, Double>>>> obtenerPreciosDetallados() {
        return cachePreciosDetallados;
    }

    private void registrarDetalle(String cadena, String sucursal, String formato, String categoria, double precio) {
        detalleEnCurso
                .computeIfAbsent(cadena, k -> new LinkedHashMap<>())
                .computeIfAbsent(sucursal, k -> new LinkedHashMap<>())
                .computeIfAbsent(formato, k -> new LinkedHashMap<>())
                .putIfAbsent(categoria, precio);
    }

    // ===================================================================
    // Tarifas reducidas por día/horario
    // ===================================================================
    // Algunas cadenas cobran menos ciertos días u horarios (ej. "Lunes a
    // Miércoles 50%", o "antes de las 13 hs"). Se guardan aparte del precio
    // regular, con la regla tal como la publica cada página, y recién se
    // aplican cuando se conoce la fecha y la hora de la función.
    // Una regla de tarifa reducida:
    // - dias / antesDe: cuándo rige. Por defecto aplica si la función cae en
    //   uno de los días O empieza antes de la hora de corte (ej. Cinema La
    //   Plata: "lunes a miércoles todo el día y antes de las 13 hs").
    // - diaYHora: exige las dos cosas a la vez (ej. el 2x1 de Cinemacenter:
    //   "de jueves a domingo, en funciones anteriores a las 18 hs").
    // - dosPorUno: la tarifa es un 2x1. El precio es la mitad de la general POR
    //   ENTRADA, pero solo comprando de a dos: no es un descuento por entrada,
    //   y así el front puede rotularla distinto de un "50% off".
    // - soloOnline: el precio vale solo comprando por web o boletería
    //   electrónica (ej. el "valor especial de jueves a domingos" de Cinemark).
    // - sinFeriadoEn: días de la semana en que la regla NO vale si ese día es
    //   feriado (ej. Atlas: "no válido los días lunes o martes feriados").
    // - desde: fecha desde la que rige, para reglas de un aumento ya anunciado
    //   (ej. Cinemark "a partir del 15/10"); null = rige desde ya.
    // - enFeriados: la regla vale también en los feriados, cualquiera sea el
    //   día de la semana (ej. Cinema Adrogué: "Jue a Dom y Feriados").
    public record TarifaReducida(double precio, Set<DayOfWeek> dias, LocalTime antesDe,
                                 boolean dosPorUno, boolean diaYHora, boolean soloOnline,
                                 Set<DayOfWeek> sinFeriadoEn, LocalDate desde, boolean enFeriados) {
        public TarifaReducida(double precio, Set<DayOfWeek> dias, LocalTime antesDe) {
            this(precio, dias, antesDe, false, false, false, Set.of(), null, false);
        }

        public TarifaReducida(double precio, Set<DayOfWeek> dias, LocalTime antesDe, boolean dosPorUno) {
            this(precio, dias, antesDe, dosPorUno, false, false, Set.of(), null, false);
        }

        TarifaReducida conDosPorUno() {
            return new TarifaReducida(precio, dias, antesDe, true, diaYHora, soloOnline, sinFeriadoEn, desde, enFeriados);
        }

        TarifaReducida conDiaYHora() {
            return new TarifaReducida(precio, dias, antesDe, dosPorUno, true, soloOnline, sinFeriadoEn, desde, enFeriados);
        }

        TarifaReducida conSoloOnline() {
            return new TarifaReducida(precio, dias, antesDe, dosPorUno, diaYHora, true, sinFeriadoEn, desde, enFeriados);
        }

        TarifaReducida conSinFeriadoEn(Set<DayOfWeek> sinFeriado) {
            return new TarifaReducida(precio, dias, antesDe, dosPorUno, diaYHora, soloOnline, sinFeriado, desde, enFeriados);
        }

        TarifaReducida conDesde(LocalDate fechaDesde) {
            return new TarifaReducida(precio, dias, antesDe, dosPorUno, diaYHora, soloOnline, sinFeriadoEn, fechaDesde, enFeriados);
        }

        TarifaReducida conEnFeriados() {
            return new TarifaReducida(precio, dias, antesDe, dosPorUno, diaYHora, soloOnline, sinFeriadoEn, desde, true);
        }

        boolean aplica(LocalDate fecha, LocalTime hora, boolean esFeriado) {
            if (desde != null && fecha.isBefore(desde)) return false;
            if (esFeriado && sinFeriadoEn.contains(fecha.getDayOfWeek())) return false;
            boolean porDia = dias.contains(fecha.getDayOfWeek()) || (esFeriado && enFeriados);
            boolean porHora = antesDe != null && hora != null && hora.isBefore(antesDe);
            return diaYHora ? (porDia && porHora) : (porDia || porHora);
        }

        // Cuándo rige, en palabras cortas, para mostrarlo junto al precio:
        // "Lun a mié", "Miércoles", "Jue a dom y feriados", "Jue a dom antes de
        // las 18 hs", "Antes de las 15 hs". Vacío si la regla no tiene ni días
        // ni hora.
        String descripcion() {
            String textoDias = describirDias(dias);
            if (enFeriados && !textoDias.isEmpty()) textoDias = textoDias + " y feriados";
            String textoHora = antesDe == null ? "" : "antes de las " + describirHora(antesDe);
            if (textoDias.isEmpty()) return capitalizar(textoHora);
            if (textoHora.isEmpty()) return textoDias;
            return diaYHora ? textoDias + " " + textoHora : textoDias + ", o " + textoHora;
        }
    }

    private static final String[] DIAS_COMPLETOS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] DIAS_CORTOS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    // "Lun a mié" (tramo seguido, también con la vuelta de domingo a lunes),
    // "Jue, sáb y dom" (días sueltos), "Miércoles" (un solo día) o "Todos los días".
    private static String describirDias(Set<DayOfWeek> dias) {
        if (dias.isEmpty()) return "";
        if (dias.size() == 7) return "Todos los días";
        boolean[] activo = new boolean[7];
        for (DayOfWeek d : dias) activo[d.getValue() - 1] = true;

        // Es un tramo seguido si hay un único día activo cuyo anterior no lo está.
        int inicio = -1;
        int comienzos = 0;
        for (int i = 0; i < 7; i++) {
            if (activo[i] && !activo[(i + 6) % 7]) {
                inicio = i;
                comienzos++;
            }
        }
        if (comienzos == 1) {
            if (dias.size() == 1) return DIAS_COMPLETOS[inicio];
            int fin = (inicio + dias.size() - 1) % 7;
            return capitalizar(DIAS_CORTOS[inicio]) + " a " + DIAS_CORTOS[fin];
        }

        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < 7; i++) if (activo[i]) nombres.add(DIAS_CORTOS[i]);
        String ultimo = nombres.remove(nombres.size() - 1);
        return capitalizar(String.join(", ", nombres)) + " y " + ultimo;
    }

    private static String describirHora(LocalTime hora) {
        return hora.getMinute() == 0 ? hora.getHour() + " hs" : String.format("%d:%02d hs", hora.getHour(), hora.getMinute());
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    // precio: lo que cuesta la entrada ESE día (la reducida si aplica, si no la
    // regular). precioRegular: la tarifa regular de ese día. precioOnline: si
    // comprando online sale menos (ver soloOnline), ese precio; si no, null.
    // precioDesde: la sucursal tiene salas premium (Premier, Comfort, Atmos...)
    // que agendadecine no distingue en sus funciones, así que el precio es el
    // de la sala base ("desde").
    // tarifaDescripcion: cuándo rige la tarifa reducida o el 2x1 que se aplicó
    // ("Lun a mié", "Jue a dom antes de las 18 hs"); null si no hay ninguna.
    public record PrecioDelDia(Double precio, Double precioRegular, boolean tarifaReducida, boolean dosPorUno,
                               Double precioOnline, boolean precioDesde, String tarifaDescripcion) {}

    // cadena → sucursal → formato → reglas. Mismas claves que cachePrecios.
    // Una sala puede tener varias tarifas reducidas (ej. Showcase: "lunes a
    // miércoles" y "matinee antes de las 15 hs"; Santa Rosa: lunes y martes
    // con precios distintos): para cada función se elige la más barata de las
    // que aplican.
    private Map<String, Map<String, Map<String, List<TarifaReducida>>>> cacheTarifasReducidas = new HashMap<>();
    // Mientras corre actualizarPrecios() los scrapers cargan acá; recién al
    // terminar pasa a cacheTarifasReducidas (mismo reemplazo que cachePrecios).
    private Map<String, Map<String, Map<String, List<TarifaReducida>>>> tarifasReducidasEnCurso = new HashMap<>();

    private void registrarReducida(String cadena, String sucursal, String formato, TarifaReducida regla) {
        // Una regla sin días ni hora de corte no se podría aplicar nunca.
        if (regla.dias().isEmpty() && regla.antesDe() == null) return;
        tarifasReducidasEnCurso
                .computeIfAbsent(cadena, k -> new HashMap<>())
                .computeIfAbsent(sucursal, k -> new HashMap<>())
                .computeIfAbsent(formato, k -> new ArrayList<>())
                .add(regla);
    }

    // Precios regulares ya anunciados con fecha de vigencia futura (ej. los
    // aumentos de Cinemark "a partir del 15/10"): cadena → sucursal → formato
    // → {fecha desde la que rige → precio}. cachePrecios tiene el precio de
    // HOY; para una función en una fecha posterior se usa el programado que ya
    // rija ese día (ver precioParaFecha).
    private Map<String, Map<String, Map<String, TreeMap<LocalDate, Double>>>> cachePreciosProgramados = new HashMap<>();
    // Mientras corre actualizarPrecios() los scrapers cargan acá (ver cachePrecios).
    private Map<String, Map<String, Map<String, TreeMap<LocalDate, Double>>>> programadosEnCurso = new HashMap<>();

    private void registrarProgramado(String cadena, String sucursal, String formato, LocalDate desde, double precio) {
        programadosEnCurso
                .computeIfAbsent(cadena, k -> new HashMap<>())
                .computeIfAbsent(sucursal, k -> new HashMap<>())
                .computeIfAbsent(formato, k -> new TreeMap<>())
                .put(desde, precio);
    }

    public Map<String, Map<String, Map<String, TreeMap<LocalDate, Double>>>> obtenerPreciosProgramados() {
        return cachePreciosProgramados;
    }

    private static final List<String> ABREVIATURAS_DIAS = List.of("lun", "mar", "mie", "jue", "vie", "sab", "dom");

    // Un día escrito completo ("lunes", "miércoles", "domingos") o abreviado
    // ("Jue", "Dom"). Las tres primeras letras lo identifican; el límite de
    // palabra evita tomar el comienzo de otras ("marzo", "mientras").
    private static final String DIA =
            "\\b(lunes|martes|miercoles|jueves|viernes|sabado|domingo|lun|mar|mie|jue|vie|sab|dom)s?\\b";

    // Lee los días de un texto de la página: "Lunes a Miércoles" o "Jue a Dom"
    // (rango), "Lunes, Martes y Miércoles" (lista) o "Todos los días".
    private Set<DayOfWeek> parsearDias(String texto) {
        String t = normalizar(texto);
        if (t.contains("todos los dias")) return EnumSet.allOf(DayOfWeek.class);

        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        Matcher rango = Pattern.compile(DIA + "\\s+a\\s+" + DIA).matcher(t);
        if (rango.find()) {
            int desde = ABREVIATURAS_DIAS.indexOf(rango.group(1).substring(0, 3));
            int hasta = ABREVIATURAS_DIAS.indexOf(rango.group(2).substring(0, 3));
            for (int i = desde; ; i = (i + 1) % 7) {
                dias.add(DayOfWeek.of(i + 1));
                if (i == hasta) break;
            }
            return dias;
        }
        Matcher lista = Pattern.compile(DIA).matcher(t);
        while (lista.find()) {
            dias.add(DayOfWeek.of(ABREVIATURAS_DIAS.indexOf(lista.group(1).substring(0, 3)) + 1));
        }
        return dias;
    }

    // Marca de "cadena" de los precios manuales dentro de Resolucion, para
    // encontrar sus tarifas reducidas (reducidasManuales).
    private static final String CADENA_MANUAL = "Manual";

    // Precios cargados a mano en la tabla cinema_manual_prices, para cines
    // cuyo precio no se puede scrapear (ej. Cines Dino, cuyo precio sale de
    // un PDF semanal) o cuya web no es confiable (ej. Gran Rex). Tienen
    // prioridad sobre cualquier otra fuente. Clave: nombre del cine del
    // directorio, normalizado (sin acentos, en minúscula).
    private Map<String, PreciosManuales> preciosManuales = new HashMap<>();

    // Tarifas reducidas de los precios manuales (hoy, 2x1 por día): nombre
    // normalizado del cine → formato → reglas.
    private Map<String, Map<String, List<TarifaReducida>>> reducidasManuales = new HashMap<>();

    // Feriados nacionales (tabla holidays), para las tarifas que no valen en
    // feriado (ver TarifaReducida.sinFeriadoEn). Los días no laborables con
    // fines turísticos NO son feriados y no van en la tabla.
    private Set<LocalDate> feriados = new HashSet<>();

    // porFormato: formato → precio general. vigenteDesde: fecha desde la que
    // rige el precio (la de la fuente), para mostrar "Actualizado al".
    private record PreciosManuales(Map<String, Double> porFormato, LocalDate vigenteDesde) {}

    private final ReentrantLock lock = new ReentrantLock();

    // De dónde salió un precio: cadena, sucursal y clave de formato en
    // cachePrecios, más el precio regular. cadena y sucursal son null cuando
    // el precio no viene de un scraper de cadena (Dinosaurio, carteleraargentina).
    private record Resolucion(String cadena, String sucursal, String clave, Double precio) {}

    public Double obtenerPrecioReferencia(String cineNombre, String formato) {
        Resolucion r = resolver(cineNombre, formato);
        return r == null ? null : r.precio();
    }

    // Igual que obtenerPrecioReferencia, pero con fecha y hora de la función:
    // si la sucursal publica una tarifa reducida que aplica ese día u horario,
    // devuelve esa, junto con la regular.
    public PrecioDelDia obtenerPrecioDelDia(String cineNombre, String formato, LocalDate fecha, LocalTime hora) {
        Resolucion r = resolver(cineNombre, formato);
        if (r == null) return null;

        // Tarifa regular que rige ESE día (puede haber un aumento programado).
        double regular = precioParaFecha(r, fecha);

        // La tarifa reducida más barata que aplique a la fecha y hora, y solo
        // si de verdad sale menos que la regular.
        TarifaReducida reducida = buscarReducida(r, fecha, hora, false);
        boolean esReducida = reducida != null && reducida.precio() < regular;
        double precio = esReducida ? reducida.precio() : regular;

        // Precio solo web / boletería electrónica, si sale menos que el que ya
        // se cobra ese día.
        TarifaReducida online = buscarReducida(r, fecha, hora, true);
        Double precioOnline = online != null && online.precio() < precio ? online.precio() : null;

        return new PrecioDelDia(precio, regular, esReducida, esReducida && reducida.dosPorUno(),
                precioOnline, tieneSalasPremium(r), esReducida ? reducida.descripcion() : null);
    }

    // Tarifa regular de la sala para la fecha de la función: la de hoy, salvo
    // que ya haya un aumento anunciado que rija ese día.
    private double precioParaFecha(Resolucion r, LocalDate fecha) {
        if (fecha == null || r.cadena() == null) return r.precio();
        var sucursales = cachePreciosProgramados.get(r.cadena());
        var porFormato = sucursales == null ? null : sucursales.get(r.sucursal());
        var versiones = porFormato == null ? null : porFormato.get(r.clave());
        if (versiones == null) return r.precio();
        var vigente = versiones.floorEntry(fecha);
        return vigente == null ? r.precio() : vigente.getValue();
    }

    // De las tarifas reducidas de esa sala (de pago normal, o solo online según
    // el parámetro), la más barata de las que aplican a la fecha y hora de la
    // función; null si ninguna aplica. Los precios cargados a mano
    // (CADENA_MANUAL) tienen sus reglas aparte.
    private TarifaReducida buscarReducida(Resolucion r, LocalDate fecha, LocalTime hora, boolean soloOnline) {
        if (r.cadena() == null || fecha == null) return null;

        List<TarifaReducida> reglas;
        if (CADENA_MANUAL.equals(r.cadena())) {
            var porFormato = reducidasManuales.get(r.sucursal());
            reglas = porFormato == null ? null : porFormato.get(r.clave());
        } else {
            var sucursales = cacheTarifasReducidas.get(r.cadena());
            var porFormato = sucursales == null ? null : sucursales.get(r.sucursal());
            reglas = porFormato == null ? null : porFormato.get(r.clave());
        }
        if (reglas == null) return null;

        boolean esFeriado = feriados.contains(fecha);
        List<TarifaReducida> aplicables = new ArrayList<>();
        for (TarifaReducida regla : reglas) {
            if (regla.soloOnline() == soloOnline && regla.aplica(fecha, hora, esFeriado)) aplicables.add(regla);
        }

        // Si ya rige una versión nueva de las reglas (un aumento anunciado),
        // las de la versión anterior no cuentan.
        LocalDate versionVigente = aplicables.stream()
                .map(TarifaReducida::desde).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        TarifaReducida mejor = null;
        for (TarifaReducida regla : aplicables) {
            if (versionVigente != null && !versionVigente.equals(regla.desde())) continue;
            if (mejor == null || regla.precio() < mejor.precio()) mejor = regla;
        }
        return mejor;
    }

    // Salas con precio propio que agendadecine NO distingue en sus funciones
    // (las manda como "2D"/"3D" a secas). DBOX, XD, 4D, IMAX y Monster Screen
    // no están: esas sí llegan con su nombre y se precian exacto.
    private static final List<String> SALAS_PREMIUM = List.of(
            "PREMIER", "COMFORT", "SUPERSEAT", "TURBO", "GRAND CRU", "ATMOS",
            "MAGNIFY", "PLATINUM", "GOLD CLASS", "VIP");

    private boolean esSalaPremium(String clave) {
        String c = clave.toUpperCase();
        return SALAS_PREMIUM.stream().anyMatch(c::contains);
    }

    // true si el precio es el de la sala base y la sucursal además tiene salas
    // premium cuyo precio es mayor: una función común puede estar en una de ellas.
    private boolean tieneSalasPremium(Resolucion r) {
        if (r.cadena() == null || r.clave() == null) return false;
        if (esSalaPremium(r.clave())) return false; // ya es el precio exacto de esa sala
        Set<String> claves;
        if (CADENA_MANUAL.equals(r.cadena())) {
            PreciosManuales manual = preciosManuales.get(r.sucursal());
            claves = manual == null ? Set.of() : manual.porFormato().keySet();
        } else {
            var sucursales = cachePrecios.get(r.cadena());
            var porFormato = sucursales == null ? null : sucursales.get(r.sucursal());
            claves = porFormato == null ? Set.of() : porFormato.keySet();
        }
        return claves.stream().anyMatch(this::esSalaPremium);
    }

    private Resolucion resolver(String cineNombre, String formato) {
        String formatoNorm = formato == null ? "" : formato.trim().toUpperCase();
        String cineNorm = normalizar(cineNombre);

        // Precio cargado a mano en la base (cinema_manual_prices): tiene
        // prioridad sobre cualquier fuente. Si el cine tiene precios manuales
        // pero ninguno para este formato, no se busca en otro lado.
        PreciosManuales manual = preciosManuales.get(cineNorm.trim());
        if (manual != null) {
            String clave = claveParaFormato(manual.porFormato(), formatoNorm);
            return clave == null ? null
                    : new Resolucion(CADENA_MANUAL, cineNorm.trim(), clave, manual.porFormato().get(clave));
        }

        // Cines del directorio cuyo nombre no contiene la clave de la fuente
        // de precios (ver EQUIVALENCIAS_DIRECTORIO): el precio sale de esa
        // sucursal puntual o no sale.
        String[] equivalencia = EQUIVALENCIAS_DIRECTORIO.get(cineNorm.trim());
        if (equivalencia != null) {
            return resolverEnSucursal(equivalencia[0], equivalencia[1], formatoNorm);
        }

        // Si el nombre trae una cadena que conocemos, el precio sale de ESA
        // cadena o no sale: si no tiene datos (ej. Cinépolis bloquea al
        // scraper) o no tiene esa sucursal, no se busca el mismo nombre
        // corto en otra cadena y se mostraría un precio ajeno.
        //
        // Un nombre SIN cadena reconocible no se busca en todas las cadenas:
        // eso asignaba precios ajenos a cines que solo comparten una palabra
        // con una sucursal (ej. "Cine Teatro Libertador" tomaba el de
        // Cinemacenter Libertad, "Cines Dino Alta Gracia" el de Cinemacenter
        // Alta Gracia). Esos cines solo reciben precio por
        // EQUIVALENCIAS_DIRECTORIO.
        String cadenaDelNombre = null;
        for (String cadena : CADENAS_CONOCIDAS) {
            if (cineNorm.contains(normalizar(cadena))) {
                if (cadenaDelNombre == null || cadena.length() > cadenaDelNombre.length()) {
                    cadenaDelNombre = cadena;
                }
            }
        }

        if (cadenaDelNombre != null) {
            Map<String, Map<String, Double>> sucursales = cachePrecios.get(cadenaDelNombre);
            if (sucursales != null) {
                String sucursal = buscarSucursal(sucursales, cineNorm);
                Resolucion r = sucursal == null ? null : resolverEnSucursal(cadenaDelNombre, sucursal, formatoNorm);
                if (r != null) return r;
            }
        }

        // Fallback: precio propio sacado de carteleraargentina.com.ar,
        // cuando la ficha del cine lo traía — match exacto por nombre
        // completo (no por "contiene"), ya que esta cache la arma
        // CarteleraArgentinaPreciosService con el nombre tal cual.
        Map<String, Double> porFormatoPropio = carteleraArgentinaPreciosService.obtenerPreciosPropiosDescubiertos().get(cineNombre);
        if (porFormatoPropio != null) {
            for (var entry : porFormatoPropio.entrySet()) {
                if (formatoNorm.startsWith(entry.getKey())) {
                    return new Resolucion(null, null, null, entry.getValue());
                }
            }
        }

        return null;
    }

    private Resolucion resolverEnSucursal(String cadena, String sucursal, String formatoNorm) {
        Map<String, Map<String, Double>> sucursales = cachePrecios.get(cadena);
        Map<String, Double> porFormato = sucursales == null ? null : sucursales.get(sucursal);
        if (porFormato == null) return null;
        String clave = claveParaFormato(porFormato, formatoNorm);
        return clave == null ? null : new Resolucion(cadena, sucursal, clave, porFormato.get(clave));
    }

    // Sucursal de la cadena cuyo nombre corto está contenido en el del cine
    // (la más larga si hay varias).
    private String buscarSucursal(Map<String, Map<String, Double>> sucursales, String cineNorm) {
        String mejorMatch = null;
        for (String clave : sucursales.keySet()) {
            if (cineNorm.contains(normalizar(clave))) {
                if (mejorMatch == null || clave.length() > mejorMatch.length()) mejorMatch = clave;
            }
        }
        return mejorMatch;
    }

    // Traduce el formato con que agendadecine etiqueta una función a la clave
    // con que se publica su precio. null = ese formato no tiene precio propio
    // publicado: no se inventa uno (mejor "sin precio" que el de otra sala).
    // Formatos observados: 2D, 3D, 4D 2D, 4D 3D, 2D DBOX, 3D DBOX, 2D XD,
    // 2D XD DBOX, XTREMO 2D, IMAX, y variantes con INFINITY VISION,
    // MONSTER SCREEN o M8.
    // Limitación: Premier, Comfort y Superseat tienen precio propio, pero
    // agendadecine los manda como "2D"/"3D" a secas, así que reciben el de
    // la sala común. El dato es una referencia base ("desde").
    private String claveDePrecio(String formatoNorm) {
        // Modificadores sin precio propio: se usa el de la sala base.
        String base = formatoNorm
                .replace("INFINITY VISION", "")
                .replace("MONSTER SCREEN", "")
                .replace("M8", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.isEmpty()) return null;
        if (base.equals("IMAX")) return "2D Y 3D";
        if (base.contains("DBOX")) {
            // DBOX dentro de una sala XD ("2D XD DBOX"): no hay precio publicado.
            return base.contains("XD") ? null : "DBOX";
        }
        if (base.startsWith("4D")) return "4D";
        if (base.equals("2D XD") || base.equals("3D XD")) return "XD " + base.substring(0, 2);
        if (base.equals("2D") || base.equals("3D")) return base;
        return null;
    }

    // Clave, entre las que publica la sucursal, cuyo precio corresponde a la
    // función; null si no hay. En orden: (1) la sala publica el formato tal
    // como lo etiqueta agendadecine (ej. Cinépolis: "4D 3D", "2D MONSTER
    // SCREEN"; Multiplex: "XTREMO 2D"); (2) Magnify 8, que agendadecine
    // etiqueta "M8" (ej. Dino Ruta 20: "2D M8"), si la sucursal publica su
    // precio; (3) lo mismo que (1) sin los modificadores sin precio propio
    // (INFINITY VISION, y M8 en las sucursales sin sala Magnify); (4) salas con
    // nombre propio (ej. Dino: "DOLBY ATMOS"); (5) la traducción a las claves
    // genéricas (ver claveDePrecio).
    private String claveParaFormato(Map<String, Double> porFormato, String formatoNorm) {
        if (porFormato.containsKey(formatoNorm)) return formatoNorm;

        // "M8" es la sala Magnify 8: si la sucursal tiene su precio, es esa sala
        // y no una sala común con un modificador.
        if ((formatoNorm.contains("M8") || formatoNorm.contains("MAGNIFY")) && porFormato.containsKey("MAGNIFY 8")) {
            return "MAGNIFY 8";
        }

        String sinModificadores = formatoNorm
                .replace("INFINITY VISION", "")
                .replace("M8", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!sinModificadores.isEmpty() && porFormato.containsKey(sinModificadores)) return sinModificadores;

        if (formatoNorm.contains("ATMOS")) {
            if (porFormato.containsKey("DOLBY ATMOS")) return "DOLBY ATMOS";
            if (porFormato.containsKey("ATMOS")) return "ATMOS";
        }

        String clave = claveDePrecio(formatoNorm);
        if (clave == null) return null;
        if (porFormato.containsKey(clave)) return clave;
        // Algunas salas publican un único precio para 2D y 3D (ej. IMAX).
        if ((clave.equals("2D") || clave.equals("3D")) && porFormato.containsKey("2D Y 3D")) return "2D Y 3D";
        return null;
    }

    /**
     * "dd/MM/yyyy" desde el que rige el precio cargado a mano de ese cine,
     * para mostrar "Actualizado al"; null si el cine no usa precio manual.
     */
    public String obtenerFechaPrecioManual(String cineNombre) {
        PreciosManuales manual = preciosManuales.get(normalizar(cineNombre).trim());
        return manual == null || manual.vigenteDesde() == null ? null
                : manual.vigenteDesde().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Lee la tabla cinema_manual_prices y reemplaza la caché de precios
     * manuales. Solo se usa la categoría GENERAL, que es la que mostramos como
     * precio de referencia; si la fila trae two_for_one_days, además se guarda
     * un 2x1 sobre ese precio. Si la lectura falla, se conserva la caché
     * anterior. Devuelve cuántos cines tienen precio manual.
     */
    public int recargarPreciosManuales() {
        recargarFeriados();
        try {
            Map<String, Map<String, Double>> porCine = new HashMap<>();
            Map<String, LocalDate> vigencia = new HashMap<>();
            Map<String, Map<String, List<TarifaReducida>>> reducidas = new HashMap<>();

            for (CinemaManualPrice p : cinemaManualPriceRepository.findAllWithCinema()) {
                if (!"GENERAL".equalsIgnoreCase(p.getCategory())) continue;
                String clave = normalizar(p.getCinema().getName()).trim();
                String formato = p.getFormat().trim().toUpperCase();
                porCine.computeIfAbsent(clave, k -> new LinkedHashMap<>()).put(formato, p.getPrice());
                // La fecha que se muestra es la más reciente entre sus precios.
                if (p.getValidFrom() != null) {
                    vigencia.merge(clave, p.getValidFrom(), (a, b) -> a.isAfter(b) ? a : b);
                }

                // 2x1 por día sobre este precio: se guarda como tarifa reducida
                // marcada dosPorUno, con la mitad de la general por entrada.
                Set<DayOfWeek> dias = diasDeLaSemana(p.getTwoForOneDays());
                if (!dias.isEmpty()) {
                    reducidas.computeIfAbsent(clave, k -> new HashMap<>())
                            .computeIfAbsent(formato, k -> new ArrayList<>())
                            .add(new TarifaReducida(p.getPrice() / 2, dias, null, true));
                }
            }

            Map<String, PreciosManuales> nuevo = new HashMap<>();
            porCine.forEach((clave, porFormato) ->
                    nuevo.put(clave, new PreciosManuales(porFormato, vigencia.get(clave))));
            preciosManuales = nuevo;
            reducidasManuales = reducidas;
            log.info("Precios manuales cargados — {} cines", nuevo.size());
            return nuevo.size();
        } catch (Exception e) {
            log.warn("No se pudieron leer los precios manuales: {} — se conservan los anteriores", e.getMessage());
            return preciosManuales.size();
        }
    }

    // Lee la tabla holidays. Si la lectura falla, se conservan los anteriores.
    private void recargarFeriados() {
        try {
            Set<LocalDate> nuevos = new HashSet<>();
            for (Holiday h : holidayRepository.findAll()) nuevos.add(h.getHolidayDate());
            feriados = nuevos;
            log.info("Feriados cargados — {}", nuevos.size());
        } catch (Exception e) {
            log.warn("No se pudieron leer los feriados: {} — se conservan los anteriores", e.getMessage());
        }
    }

    // "WEDNESDAY" o "MONDAY,TUESDAY" (nombres de java.time.DayOfWeek) → días.
    // Vacío si es null; un nombre que no se entiende se avisa y se ignora.
    private Set<DayOfWeek> diasDeLaSemana(String texto) {
        Set<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        if (texto == null) return dias;
        for (String parte : texto.split(",")) {
            try {
                dias.add(DayOfWeek.valueOf(parte.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Día no reconocido en two_for_one_days: '{}'", parte.trim());
            }
        }
        return dias;
    }

    /**
     * Calienta la cache al levantar el server — sin esto, quedaría vacía
     * hasta el primer lunes 00hs. Corre en un thread aparte para no
     * bloquear el arranque de la app mientras scrapea 9 sitios externos.
     */
    @PostConstruct
    public void calentarCacheAlArrancar() {
        new Thread(() -> {
            log.info("🎬 Calentando cache inicial de precios de cartelera...");
            actualizarPrecios();

            // Calienta también la cache de cines — es la única forma de
            // que cachePreciosPropios (el fallback de precios sacados de
            // carteleraargentina.com.ar) se llene, ya que solo se arma
            // como efecto secundario de descubrirCines(). Sin esto, esa
            // cache queda vacía hasta que algún usuario pida por
            // casualidad /cines-cercanos u /organizar-salida primero.
            log.info("🎬 Calentando cache de cines (necesaria para precios propios de carteleraargentina.com.ar)...");
            carteleraArgentinaPreciosService.obtenerCinesConUbicacion();
        }, "cartelera-precios-warmup").start();
    }

    public void actualizarPrecios() {
        lock.lock();
        try {
            Map<String, Map<String, Map<String, Double>>> nuevo = new HashMap<>();
            tarifasReducidasEnCurso = new HashMap<>();
            programadosEnCurso = new HashMap<>();
            detalleEnCurso = new HashMap<>();

            recargarPreciosManuales();

            ejecutarSinRomperElResto(nuevo, "Cinemark", this::scrapearCinemark);
            ejecutarSinRomperElResto(nuevo, "Atlas", this::scrapearAtlas);
            ejecutarSinRomperElResto(nuevo, "Cinemacenter", this::scrapearCinemacenter);
            ejecutarSinRomperElResto(nuevo, "Multiplex", this::scrapearMultiplex);
            ejecutarSinRomperElResto(nuevo, "Play Cinema", this::scrapearPlayCinema);
            ejecutarSinRomperElResto(nuevo, "Tadicor", this::scrapearTadicor);
            ejecutarSinRomperElResto(nuevo, "Santa Rosa", this::scrapearSantaRosa);
            ejecutarSinRomperElResto(nuevo, "Showcase", this::scrapearShowcase);
            ejecutarSinRomperElResto(nuevo, "Cinépolis", this::scrapearCinepolis);
            ejecutarSinRomperElResto(nuevo, "Cinema Devoto", this::scrapearCinemaDevoto);
            // Gran Rex desactivado: su web publica $1.300 (2D) y $1.450 (3D),
            // muy por debajo del resto (10.000–36.400) — casi seguro una lista
            // sin actualizar. Mejor "sin precio" que un número falso. Para
            // reactivar: descomentar la línea y confirmar el precio real.
            // ejecutarSinRomperElResto(nuevo, "Gran Rex", this::scrapearGranRex);
            ejecutarSinRomperElResto(nuevo, "Cines del Solar", this::scrapearCinesDelSolar);
            ejecutarSinRomperElResto(nuevo, "Cinema Adrogué", this::scrapearCinemaAdrogue);
            ejecutarSinRomperElResto(nuevo, "Cinema La Plata", this::scrapearCinemaLaPlata);
            ejecutarSinRomperElResto(nuevo, "Cines Paseo Aldrey", this::scrapearPaseoAldrey);

            cachePrecios = nuevo;
            cacheTarifasReducidas = tarifasReducidasEnCurso;
            cachePreciosProgramados = programadosEnCurso;
            cachePreciosDetallados = detalleEnCurso;
            int totalSucursales = nuevo.values().stream().mapToInt(Map::size).sum();
            int totalReducidas = cacheTarifasReducidas.values().stream()
                    .flatMap(porSucursal -> porSucursal.values().stream())
                    .mapToInt(Map::size).sum();
            int totalProgramados = cachePreciosProgramados.values().stream()
                    .flatMap(porSucursal -> porSucursal.values().stream())
                    .flatMap(porFormato -> porFormato.values().stream())
                    .mapToInt(TreeMap::size).sum();
            log.info("Precios de referencia actualizados — {} cadenas, {} sucursales con datos, {} tarifas reducidas, {} precios programados",
                    nuevo.size(), totalSucursales, totalReducidas, totalProgramados);
        } finally {
            lock.unlock();
        }
    }

    private interface Scraper { Map<String, Map<String, Double>> scrapear() throws Exception; }

    // Si un scraper falla, se reintenta una vez tras una pausa corta: los
    // sitios que rechazan el pedido de a ratos (ej. Cinépolis con su 403)
    // suelen responder al segundo intento.
    private static final int REINTENTOS = 1;
    private static final long PAUSA_REINTENTO_MS = 3000;

    // Si una cadena sigue fallando, se conservan los últimos datos que se
    // pudieron leer de ella, hasta esta antigüedad. Pasado ese plazo se
    // descartan: es mejor "sin precio" que un precio de hace semanas.
    private static final Duration MAX_EDAD_DATOS_VIEJOS = Duration.ofDays(14);

    // Cuándo se leyó bien cada cadena por última vez. Vive en memoria: se
    // pierde al reiniciar el servidor, igual que las cachés de precios.
    private final Map<String, LocalDateTime> ultimaActualizacionOk = new HashMap<>();

    // Cada cadena escribe en su propio namespace dentro de "destino" —
    // antes todas compartían un único mapa plano de sucursal→precio, y
    // si dos cadenas tenían una sucursal con el mismo nombre corto (ej.
    // "Quilmes" en Cinemark Y en Showcase), la segunda en correr pisaba
    // silenciosamente el precio de la primera sin ningún aviso.
    //
    // Si el scraper falla (o devuelve 0 sucursales, señal de que la página
    // cambió), se reintenta y, si sigue fallando, se conserva lo último que
    // se leyó bien de esa cadena en vez de dejarla sin datos.
    private void ejecutarSinRomperElResto(Map<String, Map<String, Map<String, Double>>> destino, String cadena, Scraper scraper) {
        Exception ultimoError = null;
        for (int intento = 0; intento <= REINTENTOS; intento++) {
            try {
                Map<String, Map<String, Double>> resultado = scraper.scrapear();
                if (resultado.isEmpty()) {
                    throw new IllegalStateException("no devolvió ninguna sucursal");
                }
                destino.put(cadena, resultado);
                ultimaActualizacionOk.put(cadena, LocalDateTime.now());
                return;
            } catch (Exception e) {
                ultimoError = e;
                // Si el scraper falló a mitad de camino, tampoco se dejan sus
                // tarifas reducidas, precios programados ni detalle a medias.
                tarifasReducidasEnCurso.remove(cadena);
                programadosEnCurso.remove(cadena);
                detalleEnCurso.remove(cadena);
                // Un certificado inválido no se arregla reintentando.
                if (e instanceof javax.net.ssl.SSLException || intento == REINTENTOS) break;
                log.info("Falló {} ({}), reintento en {} s", cadena, e.getMessage(), PAUSA_REINTENTO_MS / 1000);
                try {
                    Thread.sleep(PAUSA_REINTENTO_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        conservarDatosAnteriores(destino, cadena, ultimoError);
    }

    // cachePrecios y cacheTarifasReducidas todavía tienen la corrida
    // anterior: recién se reemplazan al final de actualizarPrecios().
    private void conservarDatosAnteriores(Map<String, Map<String, Map<String, Double>>> destino, String cadena, Exception error) {
        String motivo = error == null ? "sin detalle" : error.getMessage();
        var anteriores = cachePrecios.get(cadena);
        LocalDateTime ultimaOk = ultimaActualizacionOk.get(cadena);

        if (anteriores == null || ultimaOk == null) {
            log.warn("No se pudo actualizar precios de {}: {} (no hay datos anteriores)", cadena, motivo);
            return;
        }

        Duration edad = Duration.between(ultimaOk, LocalDateTime.now());
        if (edad.compareTo(MAX_EDAD_DATOS_VIEJOS) > 0) {
            log.warn("No se pudo actualizar precios de {}: {} — los datos anteriores tienen {} días y se descartan",
                    cadena, motivo, edad.toDays());
            return;
        }

        destino.put(cadena, anteriores);
        var reducidasAnteriores = cacheTarifasReducidas.get(cadena);
        if (reducidasAnteriores != null) tarifasReducidasEnCurso.put(cadena, reducidasAnteriores);
        var programadosAnteriores = cachePreciosProgramados.get(cadena);
        if (programadosAnteriores != null) programadosEnCurso.put(cadena, programadosAnteriores);
        var detalleAnterior = cachePreciosDetallados.get(cadena);
        if (detalleAnterior != null) detalleEnCurso.put(cadena, detalleAnterior);
        log.warn("No se pudo actualizar precios de {}: {} — se conservan los de hace {} h",
                cadena, motivo, edad.toHours());
    }

    // ===================================================================
    // Helpers comunes
    // ===================================================================

    private void agregarPrecio(Map<String, Map<String, Double>> destino, String sucursal, String formato, double precio) {
        destino.computeIfAbsent(sucursal, k -> new LinkedHashMap<>()).put(formato, precio);
    }

    private double parsearPrecio(String texto) {
        return Double.parseDouble(texto.replace(".", "").replace(",", "").trim());
    }

    private String normalizar(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    // Etiqueta de una fila de precios → clave de categoría estable: sin tildes,
    // en mayúsculas, sin aclaraciones entre paréntesis ni el sufijo "- NAP", y
    // con guiones bajos. Ej.: "MENOR (**) - NAP" → "MENOR"; "LUNES A MIÉRCOLES
    // (Hasta el 28/02/27)" → "LUNES_A_MIERCOLES".
    private String categoriaDesdeEtiqueta(String etiqueta) {
        String s = normalizar(etiqueta).toUpperCase()
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("-\\s*NAP\\b", " ")
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "OTRA" : s;
    }

    // "2X1 EN FUNCIONES ANTERIORES A LAS 18 HS : Sobre entrada general de
    // Jueves a Domingo." (Cinemacenter y Paseo Aldrey): un 2x1 sobre la entrada
    // general, solo los días que dice la página Y antes de esa hora.
    private static final Pattern DOS_POR_UNO_ANTES_DE = Pattern.compile(
            "2\\s*X\\s*1\\s+EN\\s+FUNCIONES\\s+ANTERIORES\\s+A\\s+LAS\\s*(\\d{1,2})\\s*HS\\s*:\\s*(.{0,90})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private void registrarDosPorUnoAntesDe(String cadena, String sucursal, String formato,
                                           String bloque, double precioGeneral) {
        Matcher m = DOS_POR_UNO_ANTES_DE.matcher(bloque);
        if (!m.find()) return;
        // Los días terminan en el punto o donde arranca la próxima promoción
        // ("50% OFF ..."): se corta ahí para no leer los de esa.
        String diasTexto = m.group(2).split("\\.|(?i)50\\s*%")[0];
        Set<DayOfWeek> dias = parsearDias(diasTexto);
        if (dias.isEmpty()) return;
        LocalTime corte = LocalTime.of(Math.min(23, Integer.parseInt(m.group(1))), 0);
        registrarReducida(cadena, sucursal, formato,
                new TarifaReducida(precioGeneral / 2, dias, corte).conDosPorUno().conDiaYHora());
    }

    // ===================================================================
    // Cinemark — la página trae un bloque por complejo (el nombre es un
    // h2) con las tarifas vigentes y, a continuación, los aumentos ya
    // programados ("Precios vigentes a partir del día dd/MM/yyyy
    // inclusive"). Los complejos se leen de la propia página, no de una
    // lista fija, y se guardan todos los tipos de sala que publica (2D,
    // 3D, DBOX, XD, Premier, Comfort, 4D).
    //
    // De cada complejo:
    // - El bloque que rige HOY da el precio general, las tarifas reducidas
    //   por día ("LUNES A MIÉRCOLES", "MIÉRCOLES", "LUNES A DOMINGO"...), el
    //   valor especial solo web ("VALOR ESPECIAL DE JUEVES A DOMINGOS (Solo Web
    //   y Boleterías Electrónicas)") y el detalle por categoría.
    // - Los bloques con fecha futura se guardan como PRECIOS PROGRAMADOS, con
    //   sus tarifas reducidas marcadas con esa fecha: una función en una fecha
    //   posterior usa el precio que ya rija ese día.
    // ===================================================================
    private static final Pattern CINEMARK_BLOQUE_FECHADO =
            Pattern.compile("Precios vigentes a partir del d[ií]a\\s+(\\d{2}/\\d{2}/\\d{4})");

    // Encabezado de una sala: "SALAS 2D $ 18.400..." / "BUTACAS DBOX (2) $ ...".
    private static final Pattern CINEMARK_ENCABEZADO = Pattern.compile("^(?:SALAS|BUTACAS)\\s+(.+?)\\s*\\$");
    private static final Pattern CINEMARK_GENERAL = Pattern.compile("\\$\\s*([\\d.,]+)\\s*GENERAL\\b");

    // Fila de tarifa reducida por día dentro de una sala: "$ 9.200 LUNES A
    // MIÉRCOLES (Hasta el 28/02/27) Precio sin impuestos...". Solo entran las
    // filas cuya etiqueta arranca con un día.
    private static final Pattern CINEMARK_REDUCIDA = Pattern.compile(
            "\\$\\s*([\\d.,]+)\\s+((?:LUNES|MARTES|MI[EÉ]RCOLES)[^$]*?)\\s+Precio sin impuestos");

    // "$ 13.900 VALOR ESPECIAL DE JUEVES A DOMINGOS (Solo Web y Boleterías
    // Electrónicas) - NAP": precio por canal de venta, no para todos. La página
    // trae un error de tipeo en alguna sala ("ALOR ESPECIAL"), de ahí el V?.
    private static final Pattern CINEMARK_SOLO_ONLINE = Pattern.compile(
            "\\$\\s*([\\d.,]+)\\s+V?ALOR ESPECIAL DE\\s+([^()$]+?)\\s*\\(\\s*Solo [^)]*\\)");

    // Cualquier fila "$ precio ETIQUETA Precio sin impuestos...": la etiqueta
    // arranca con una letra y no puede contener "$", para no confundir el
    // "precio sin impuestos" de una fila con el comienzo de la siguiente.
    private static final Pattern CINEMARK_FILA = Pattern.compile(
            "\\$\\s*([\\d.,]+)\\s+([A-Za-zÁÉÍÓÚÑáéíóúñ][^$]*?)\\s+Precio sin impuestos");

    private record BloqueCinemark(LocalDate desde, String texto) {}

    private Map<String, Map<String, Double>> scrapearCinemark() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemark.com.ar/precios").userAgent(UA).timeout(20000).get();
        return parsearCinemark(doc.select("h2").eachText(), doc.text(), LocalDate.now());
    }

    // Recibe los nombres de complejo y el texto de la página por separado de la
    // descarga, para poder probar el parser con contenido conocido.
    Map<String, Map<String, Double>> parsearCinemark(List<String> encabezadosH2, String texto, LocalDate hoy) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Posición de cada complejo dentro del texto, en orden de aparición.
        List<String> nombres = new ArrayList<>();
        List<Integer> inicios = new ArrayList<>();
        int cursor = 0;
        for (String h2 : encabezadosH2) {
            String nombre = h2.trim();
            if (nombre.isBlank()) continue;
            int pos = texto.indexOf(nombre, cursor);
            if (pos < 0) continue;
            nombres.add(nombre);
            inicios.add(pos);
            cursor = pos + nombre.length();
        }

        for (int i = 0; i < nombres.size(); i++) {
            int desde = inicios.get(i) + nombres.get(i).length();
            int hasta = (i + 1 < nombres.size()) ? inicios.get(i + 1) : texto.length();
            List<BloqueCinemark> bloques = bloquesDeCinemark(texto.substring(desde, hasta));
            BloqueCinemark vigente = elegirBloqueVigente(bloques, hoy);

            for (BloqueCinemark bloque : bloques) {
                if (bloque == vigente) {
                    procesarBloqueCinemark(nombres.get(i), bloque, true, hoy, resultado);
                } else if (bloque.desde() != null && bloque.desde().isAfter(hoy)) {
                    procesarBloqueCinemark(nombres.get(i), bloque, false, hoy, resultado);
                }
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Cinemark: 0 complejos parseados — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // Un complejo trae su bloque "Tarifas Vigentes" (sin fecha) y, después, uno
    // o más bloques con aumentos ya programados, cada uno con su fecha.
    private List<BloqueCinemark> bloquesDeCinemark(String segmento) {
        Matcher m = CINEMARK_BLOQUE_FECHADO.matcher(segmento);
        List<Integer> cortes = new ArrayList<>();
        List<LocalDate> fechas = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        while (m.find()) {
            cortes.add(m.start());
            fechas.add(LocalDate.parse(m.group(1), fmt));
        }

        List<BloqueCinemark> bloques = new ArrayList<>();
        bloques.add(new BloqueCinemark(null, segmento.substring(0, cortes.isEmpty() ? segmento.length() : cortes.get(0))));
        for (int i = 0; i < cortes.size(); i++) {
            int hasta = (i + 1 < cortes.size()) ? cortes.get(i + 1) : segmento.length();
            bloques.add(new BloqueCinemark(fechas.get(i), segmento.substring(cortes.get(i), hasta)));
        }
        return bloques;
    }

    // El bloque que rige hoy: el de fecha más reciente que ya empezó; si
    // todavía ninguno empezó, el de "Tarifas Vigentes" (el primero, sin fecha).
    private BloqueCinemark elegirBloqueVigente(List<BloqueCinemark> bloques, LocalDate hoy) {
        BloqueCinemark elegido = null;
        for (BloqueCinemark b : bloques) {
            if (b.desde() != null && b.desde().isAfter(hoy)) continue;
            boolean masReciente = elegido == null
                    || (b.desde() != null && (elegido.desde() == null || b.desde().isAfter(elegido.desde())));
            if (masReciente) elegido = b;
        }
        return elegido;
    }

    private void procesarBloqueCinemark(String complejo, BloqueCinemark bloque, boolean vigente,
                                        LocalDate hoy, Map<String, Map<String, Double>> resultado) {
        LocalDate desde = bloque.desde();

        for (String sala : bloque.texto().split("(?=\\b(?:SALAS|BUTACAS)\\s)")) {
            Matcher mEnc = CINEMARK_ENCABEZADO.matcher(sala.trim());
            if (!mEnc.find()) continue;
            // "3D (1)" → "3D": el número entre paréntesis es una nota al pie.
            String formato = mEnc.group(1).replaceAll("\\s*\\(\\d+\\)\\s*$", "").trim().toUpperCase();

            Matcher mGeneral = CINEMARK_GENERAL.matcher(sala);
            if (!mGeneral.find()) continue;
            double general = parsearPrecio(mGeneral.group(1));

            if (vigente) {
                agregarPrecio(resultado, complejo, formato, general);
                Matcher mFila = CINEMARK_FILA.matcher(sala);
                while (mFila.find()) {
                    registrarDetalle("Cinemark", complejo, formato,
                            categoriaDesdeEtiqueta(mFila.group(2)), parsearPrecio(mFila.group(1)));
                }
            } else {
                registrarProgramado("Cinemark", complejo, formato, desde, general);
            }

            // Tarifa reducida por día de esta sala, si la publica y no está
            // vencida. Los días se leen de su etiqueta. En un bloque futuro
            // lleva su fecha de vigencia.
            Matcher mRed = CINEMARK_REDUCIDA.matcher(sala);
            if (mRed.find() && !reglaVencida(mRed.group(2), hoy)) {
                TarifaReducida regla = new TarifaReducida(
                        parsearPrecio(mRed.group(1)), parsearDias(mRed.group(2)), null);
                registrarReducida("Cinemark", complejo, formato, vigente ? regla : regla.conDesde(desde));
            }

            // Valor especial solo web / boletería electrónica.
            Matcher mOnline = CINEMARK_SOLO_ONLINE.matcher(sala);
            if (mOnline.find()) {
                TarifaReducida regla = new TarifaReducida(
                        parsearPrecio(mOnline.group(1)), parsearDias(mOnline.group(2)), null).conSoloOnline();
                registrarReducida("Cinemark", complejo, formato, vigente ? regla : regla.conDesde(desde));
            }
        }
    }

    // Algunas tarifas reducidas traen su vencimiento en la etiqueta ("... (Hasta
    // el 28/02/27)"). Si ya venció y la página todavía la lista, no se aplica.
    private boolean reglaVencida(String etiqueta, LocalDate hoy) {
        Matcher m = Pattern.compile("Hasta el (\\d{2})/(\\d{2})/(\\d{2})").matcher(etiqueta);
        if (!m.find()) return false;
        try {
            LocalDate vence = LocalDate.of(2000 + Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            return vence.isBefore(hoy);
        } catch (Exception e) {
            return false;
        }
    }

    // ===================================================================
    // Atlas — sitio oficial (atlascines.com), mismo sistema DynamicPages
    // que Tadicor y Santa Rosa. Estructura: h3 = complejo, h4 = "Salas
    // 2D/3D/4D", y debajo una tabla con General / Menores, Mayores y
    // Discap. / Lunes a Miércoles / Promo Jue a Dom. Se toma "General"
    // como tarifa regular y "Lunes a Miércoles" y "Promo Jue a Dom" como
    // reducidas; todas las filas van además al detalle por categoría. La
    // página de Atlas no aclara a quién aplica "Promo Jue a Dom" (Liniers,
    // Flores, Catán), pero Cinema Adrogué (mismo sistema y misma cadena en el
    // directorio) publica la misma fila como "Jue a Dom y Feriados
    // (Promoción)": una tarifa por día, abierta a todos. La página aclara que
    // el precio de lunes a miércoles no vale en los lunes o martes feriados:
    // eso también se lee de ahí.
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearAtlas() throws Exception {
        Document doc = Jsoup.connect("https://www.atlascines.com/DynamicPages?id_section=35")
                .userAgent(UA).timeout(20000).get();
        return parsearAtlas(doc);
    }

    Map<String, Map<String, Double>> parsearAtlas(Document doc) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // "Precio Lunes a Miércoles no válidos los días lunes o martes feriados."
        Set<DayOfWeek> sinFeriadoEn = EnumSet.noneOf(DayOfWeek.class);
        Matcher mFeriados = Pattern.compile("(?i)no v[aá]lidos?\\s+los\\s+d[ií]as\\s+(.+?)\\s+feriados")
                .matcher(doc.text());
        if (mFeriados.find()) sinFeriadoEn = parsearDias(mFeriados.group(1));

        Pattern patronSala = Pattern.compile("(?i)^Salas\\s+(\\w+)");
        String complejoActual = null;
        String formatoActual = null;

        for (Element el : doc.select("h3, h4, table")) {
            if (el.tagName().equals("h3")) {
                complejoActual = el.text().trim();
                formatoActual = null;
            } else if (el.tagName().equals("h4")) {
                Matcher ms = patronSala.matcher(el.text().trim());
                if (ms.find()) formatoActual = ms.group(1).toUpperCase();
            } else if (complejoActual != null && formatoActual != null) {
                for (Element fila : el.select("tr")) {
                    List<Element> celdas = fila.select("td, th");
                    if (celdas.size() < 2) continue;
                    String etiqueta = celdas.get(0).text().trim();
                    Matcher mp = Pattern.compile("\\$\\s*([\\d.,]+)").matcher(celdas.get(celdas.size() - 1).text());
                    if (!mp.find()) continue;
                    double precio = parsearPrecio(mp.group(1));

                    registrarDetalle("Atlas", complejoActual, formatoActual, categoriaDesdeEtiqueta(etiqueta), precio);

                    if (etiqueta.toUpperCase().startsWith("GENERAL")) {
                        agregarPrecio(resultado, complejoActual, formatoActual, precio);
                    } else if (normalizar(etiqueta).startsWith("lunes")) {
                        // "Lunes a Miércoles": los días se leen de la etiqueta.
                        registrarReducida("Atlas", complejoActual, formatoActual,
                                new TarifaReducida(precio, parsearDias(etiqueta), null).conSinFeriadoEn(sinFeriadoEn));
                    } else if (normalizar(etiqueta).startsWith("promo")) {
                        // "Promo Jue a Dom": tarifa por día, los días salen de la etiqueta.
                        Set<DayOfWeek> dias = parsearDias(etiqueta);
                        if (!dias.isEmpty()) {
                            registrarReducida("Atlas", complejoActual, formatoActual,
                                    new TarifaReducida(precio, dias, null));
                        }
                    }
                }
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Atlas: 0 complejos parseados — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // ===================================================================
    // Cinemacenter — la página agrupa por CIUDAD y, dentro de cada una,
    // repite un bloque por tipo de sala: "Entradas 2D: {nombre del cine}.
    // ENTRADA GENERAL : ... $ {precio}". El nombre no siempre empieza con
    // "Cinemacenter" ni termina en punto, y a veces nombra varios cines con
    // un solo precio (Mar del Plata: "Ambassador - Cinema - Diagonal"). Se
    // parsea bloque por bloque y se guarda el precio GENERAL de cada cine,
    // más su tarifa "LUNES A MIÉRCOLES 50%OFF" y su "2X1 EN FUNCIONES
    // ANTERIORES A LAS 18 HS" (de jueves a domingo) cuando las publica.
    // ===================================================================
    private static final Pattern CINEMACENTER_BLOQUE = Pattern.compile(
            "Entradas\\s+([^:]{1,40}):\\s*(.*?)(?=Entradas\\s+[^:]{1,40}:|Formas de Pago:|$)", Pattern.DOTALL);
    private static final Pattern CINEMACENTER_GENERAL = Pattern.compile(
            "^(.*?)ENTRADA GENERAL\\s*:[^$]{0,80}?\\$\\s*([\\d.,]+)", Pattern.DOTALL);
    private static final Pattern CINEMACENTER_REDUCIDA = Pattern.compile(
            "(LUNES[^:$]{0,30}?)\\s*50\\s*%\\s*OFF\\s*:[^$]{0,120}?\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);

    private Map<String, Map<String, Double>> scrapearCinemacenter() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemacenter.com.ar/precios#contenido").userAgent(UA).timeout(20000).get();
        return parsearCinemacenter(doc.text());
    }

    Map<String, Map<String, Double>> parsearCinemacenter(String texto) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher bloque = CINEMACENTER_BLOQUE.matcher(texto);
        while (bloque.find()) {
            String formato = formatoCinemacenter(bloque.group(1).trim());
            Matcher general = CINEMACENTER_GENERAL.matcher(bloque.group(2));
            // Bloque sin precio publicado (ej. el 3D de Miramar): se saltea.
            if (!general.find()) continue;
            double precio = parsearPrecio(general.group(2));

            // Tarifa reducida del mismo bloque, si la publica:
            // "LUNES A MIÉRCOLES 50%OFF : ... $ 7500".
            TarifaReducida reducida = null;
            Matcher mRed = CINEMACENTER_REDUCIDA.matcher(bloque.group(2));
            if (mRed.find()) {
                reducida = new TarifaReducida(parsearPrecio(mRed.group(2)), parsearDias(mRed.group(1)), null);
            }

            String nombres = general.group(1).replaceAll("[.\\s]+$", "");
            for (String nombre : nombres.split("\\s+-\\s+")) {
                String sucursal = sucursalCinemacenter(nombre);
                if (sucursal.isBlank()) continue;
                agregarPrecio(resultado, sucursal, formato, precio);
                if (reducida != null) registrarReducida("Cinemacenter", sucursal, formato, reducida);
                // "2X1 EN FUNCIONES ANTERIORES A LAS 18 HS : Sobre entrada
                // general de Jueves a Domingo": días Y horario a la vez.
                registrarDosPorUnoAntesDe("Cinemacenter", sucursal, formato, bloque.group(2), precio);
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Cinemacenter: 0 sucursales parseadas — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // "2D" y "3D" quedan tal cual; las salas premium (Turbo/Dolby Atmos y
    // Grand Cru) se guardan con clave propia aunque agendadecine hoy las
    // manda como "2D" y no se puedan distinguir.
    private String formatoCinemacenter(String tipo) {
        String t = tipo.toUpperCase();
        if (t.startsWith("TURBO")) return "TURBO";
        if (t.contains("GRAND CRU")) return "GRAND CRU";
        return t;
    }

    private String sucursalCinemacenter(String nombre) {
        String n = nombre.replaceFirst("(?i)^Cinemacenter\\s+", "").trim();
        // La página llama "Cinema" a la sala Los Gallegos Shopping (ver su
        // sección "Formas de Pago"). Como clave suelta, "Cinema" quedaría
        // contenida en el nombre de cualquier "Cinemacenter X" y le daría
        // su precio a cines que no tienen uno propio.
        return n.equalsIgnoreCase("Cinema") ? "Cinema Los Gallegos" : n;
    }

    // ===================================================================
    // Cines Paseo Aldrey (Mar del Plata) — sitio propio dentro del circuito
    // Cinemacenter (cinespaseoaldrey.com.ar/precios), que la página de
    // Cinemacenter no lista. Un bloque por tipo de sala ("ENTRADAS 2D",
    // "ENTRADAS 3D", "ENTRADAS SALA ATMOS") con la entrada general (jueves a
    // martes), "MIÉRCOLES 50%OFF: ... $ 7500" y, en 2D y 3D, "2X1 EN FUNCIONES
    // ANTERIORES A LAS 18 HS".
    // ===================================================================
    private static final Pattern ALDREY_BLOQUE = Pattern.compile(
            "ENTRADAS\\s+(2D|3D|SALA\\s+ATMOS)\\s+(.*?)(?=ENTRADAS\\s+(?:2D|3D|SALA)|FORMAS DE PAGO|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ALDREY_GENERAL = Pattern.compile(
            "ENTRADA GENERAL\\s*:[^$]{0,80}?\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALDREY_REDUCIDA = Pattern.compile(
            "((?:LUNES|MARTES|MI[EÉ]RCOLES|JUEVES|VIERNES|S[AÁ]BADO|DOMINGO)S?)\\s*50\\s*%\\s*OFF\\s*:[^$]{0,120}?\\$\\s*([\\d.,]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Map<String, Map<String, Double>> scrapearPaseoAldrey() throws Exception {
        Document doc = Jsoup.connect("https://www.cinespaseoaldrey.com.ar/precios#contenido").userAgent(UA).timeout(20000).get();
        return parsearPaseoAldrey(doc.text());
    }

    Map<String, Map<String, Double>> parsearPaseoAldrey(String texto) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher bloque = ALDREY_BLOQUE.matcher(texto);
        while (bloque.find()) {
            String tipo = bloque.group(1).toUpperCase().replaceAll("\\s+", " ");
            String formato = tipo.startsWith("SALA") ? "ATMOS" : tipo;

            Matcher general = ALDREY_GENERAL.matcher(bloque.group(2));
            if (!general.find()) continue;
            double precio = parsearPrecio(general.group(1));
            agregarPrecio(resultado, "Paseo Aldrey", formato, precio);

            // Tarifa reducida del mismo bloque; el día se lee de su etiqueta.
            Matcher reducida = ALDREY_REDUCIDA.matcher(bloque.group(2));
            if (reducida.find()) {
                registrarReducida("Cines Paseo Aldrey", "Paseo Aldrey", formato,
                        new TarifaReducida(parsearPrecio(reducida.group(2)), parsearDias(reducida.group(1)), null));
            }

            registrarDosPorUnoAntesDe("Cines Paseo Aldrey", "Paseo Aldrey", formato, bloque.group(2), precio);
        }

        if (resultado.isEmpty()) {
            log.warn("Paseo Aldrey: 0 salas parseadas — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // ===================================================================
    // Multiplex — ALTA confianza (tabla HTML real). Cada complejo trae su
    // propia tabla, con las columnas en distinto orden (Lavalle y Belgrano
    // ponen las promociones antes de la tarifa general; Canning, Pilar y San
    // Juan, la general primero y "Lunes a Miércoles" al final) y con
    // encabezados de dos filas. Por eso cada tabla se expande a una grilla y
    // la columna "Tarifa general" y las de promoción con días se buscan por
    // el texto de su encabezado. Se guardan 2D, 3D, 4D (2D y 3D por
    // separado), Xtremo, Comfort y Platinum. Las promociones con días ("Lunes
    // a Miércoles", "I LOVE MULTIPLEX (jueves a domingo)", "Promo Precios
    // bajos (jueves, sábado y domingo)", "Viernes de Cine") son tarifas
    // reducidas por día: la página las publica bajo "Comprando en Boletería y
    // Online", sobre la tarifa de lista, sin pedir tarjeta ni cupón. "Otras
    // promociones" (los 2x1 con tarjeta o código) no tiene días y no se usa.
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearMultiplex() throws Exception {
        Document doc = Jsoup.connect("https://multiplex.com.ar/precios/").userAgent(UA).timeout(20000).get();
        return parsearMultiplex(doc);
    }

    Map<String, Map<String, Double>> parsearMultiplex(Document doc) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();
        Pattern patronPrecio = Pattern.compile("\\$\\s*([\\d.,]+)");

        // El nombre de sucursal vive en un acordeón de Elementor
        // (div.e-n-accordion-item-title-text), no en un heading normal —
        // confirmado con HTML real. Se recorre el documento en orden
        // (nombre de sucursal y tablas), con este selector puntual.
        String sucursalActual = null;
        for (var el : doc.select("div.e-n-accordion-item-title-text, table")) {
            if (!el.tagName().equals("table")) {
                String texto = el.text().trim();
                if (texto.startsWith("Multiplex ")) sucursalActual = texto.substring("Multiplex ".length()).trim();
                continue;
            }
            if (sucursalActual == null) continue;

            List<List<String>> grilla = expandirTabla(el);

            // Los encabezados son las filas anteriores a la primera con precios.
            int primeraDeDatos = -1;
            for (int i = 0; i < grilla.size() && primeraDeDatos < 0; i++) {
                if (grilla.get(i).stream().anyMatch(c -> c.contains("$"))) primeraDeDatos = i;
            }
            if (primeraDeDatos < 0) continue;

            // Columna "Tarifa general" y columnas de promoción con días: se
            // buscan por el texto de su encabezado (sumando las filas de
            // encabezado). Una promoción con días es "Lunes a Miércoles", "I
            // LOVE MULTIPLEX (jueves a domingo)", "Promo Precios bajos (jueves,
            // sábado y domingo)" o "Viernes de Cine": la página las publica
            // bajo "Comprando en Boletería y Online", sobre la tarifa de lista,
            // sin pedir tarjeta ni cupón.
            int columnaGeneral = -1;
            List<Integer> columnasPromo = new ArrayList<>();
            List<Set<DayOfWeek>> diasPromo = new ArrayList<>();
            int ancho = grilla.get(primeraDeDatos).size();
            for (int c = 0; c < ancho; c++) {
                StringBuilder encabezado = new StringBuilder();
                for (int f = 0; f < primeraDeDatos; f++) {
                    List<String> filaEncabezado = grilla.get(f);
                    if (c < filaEncabezado.size()) encabezado.append(' ').append(filaEncabezado.get(c));
                }
                String normalizado = normalizar(encabezado.toString());
                if (columnaGeneral < 0 && normalizado.contains("general")) columnaGeneral = c;
                if (normalizado.contains("promo")) {
                    Set<DayOfWeek> dias = parsearDias(encabezado.toString());
                    if (!dias.isEmpty()) {
                        columnasPromo.add(c);
                        diasPromo.add(dias);
                    }
                }
            }

            for (int i = primeraDeDatos; i < grilla.size(); i++) {
                List<String> fila = grilla.get(i);
                if (fila.isEmpty()) continue;
                String sala = fila.get(0).trim();
                if (sala.isBlank() || sala.equalsIgnoreCase("Sala")) continue;

                String clave = claveMultiplex(sala);
                if (clave == null) continue;

                // Precio de la columna "Tarifa general"; si no se la encontró o
                // la fila no trae precio ahí, el más alto de la fila (las
                // promociones y descuentos son todos ≤ que la tarifa de lista).
                Double regular = null;
                if (columnaGeneral >= 0 && columnaGeneral < fila.size()) {
                    Matcher mg = patronPrecio.matcher(fila.get(columnaGeneral));
                    if (mg.find()) regular = parsearPrecio(mg.group(1));
                }
                if (regular == null) {
                    for (int c = 1; c < fila.size(); c++) {
                        Matcher mp = patronPrecio.matcher(fila.get(c));
                        if (mp.find()) {
                            double v = parsearPrecio(mp.group(1));
                            if (regular == null || v > regular) regular = v;
                        }
                    }
                }
                if (regular == null) continue;

                Map<String, Double> porFormato = resultado.computeIfAbsent(sucursalActual, k -> new LinkedHashMap<>());
                // Ya está si es otra fila del mismo formato: no se pisa, y
                // tampoco se repite su reducida.
                if (porFormato.containsKey(clave)) continue;
                porFormato.put(clave, regular);

                // Una tarifa reducida por cada columna de promoción con precio en
                // esta fila ("No aplica" no trae precio). Si una promoción sale
                // igual o más que la de lista (ej. la 4D de Lavalle), no se usa:
                // obtenerPrecioDelDia solo aplica las que bajan el precio.
                for (int k = 0; k < columnasPromo.size(); k++) {
                    int columna = columnasPromo.get(k);
                    if (columna >= fila.size()) continue;
                    Matcher mr = patronPrecio.matcher(fila.get(columna));
                    if (mr.find()) {
                        registrarReducida("Multiplex", sucursalActual, clave,
                                new TarifaReducida(parsearPrecio(mr.group(1)), diasPromo.get(k), null));
                    }
                }
            }
        }
        return resultado;
    }

    // Fila de la tabla ("2D", "2DXtremo", "4D (3D) con anteojos", "2D
    // ComfortPlus", "Platinum", "4D/2D sin antejos"...) → clave con que se
    // guarda su precio. null si no se reconoce. Las claves de 4D y Xtremo
    // coinciden con los formatos que manda agendadecine ("4D 3D", "XTREMO 2D").
    private String claveMultiplex(String sala) {
        String s = normalizar(sala);
        if (s.contains("platinum")) return "PLATINUM";
        if (s.contains("comfort")) return "COMFORT " + (s.contains("3d") ? "3D" : "2D");
        if (s.contains("4d")) return "4D " + ((s.contains("3d") || s.contains("con anteojos")) ? "3D" : "2D");
        if (s.contains("xtremo")) return "XTREMO " + (s.contains("3d") ? "3D" : "2D");
        if (s.startsWith("3d")) return "3D";
        if (s.startsWith("2d")) return "2D";
        return null;
    }

    // Expande una tabla HTML a una grilla de texto, repitiendo el contenido de
    // las celdas con colspan (columnas) y rowspan (filas): así cada fila queda
    // con una celda por columna y los encabezados de dos filas se alinean con
    // los datos.
    private List<List<String>> expandirTabla(Element tabla) {
        List<List<String>> grilla = new ArrayList<>();
        // columna → {texto, filas que le quedan por ocupar}: celdas con rowspan
        // de filas anteriores que todavía ocupan esa columna.
        Map<Integer, String[]> ocupadas = new HashMap<>();

        for (Element fila : tabla.select("tr")) {
            List<String> actual = new ArrayList<>();
            for (Element celda : fila.children()) {
                if (!celda.tagName().equals("td") && !celda.tagName().equals("th")) continue;
                tomarOcupadas(actual, ocupadas);
                String texto = celda.text().trim();
                int colspan = Math.max(1, numeroOCero(celda.attr("colspan")));
                int rowspan = Math.max(1, numeroOCero(celda.attr("rowspan")));
                for (int i = 0; i < colspan; i++) {
                    if (rowspan > 1) ocupadas.put(actual.size(), new String[]{texto, String.valueOf(rowspan - 1)});
                    actual.add(texto);
                }
            }
            tomarOcupadas(actual, ocupadas);
            grilla.add(actual);
        }
        return grilla;
    }

    // Mientras la próxima columna esté ocupada por un rowspan de una fila
    // anterior, se rellena con el texto de esa celda.
    private void tomarOcupadas(List<String> fila, Map<Integer, String[]> ocupadas) {
        String[] ocupada;
        while ((ocupada = ocupadas.get(fila.size())) != null) {
            int columna = fila.size();
            fila.add(ocupada[0]);
            int restantes = Integer.parseInt(ocupada[1]) - 1;
            if (restantes <= 0) ocupadas.remove(columna);
            else ocupada[1] = String.valueOf(restantes);
        }
    }

    private int numeroOCero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================================================================
    // Play Cinema — un solo complejo (San Juan), precio simple
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearPlayCinema() throws Exception {
        Document doc = Jsoup.connect("https://playcinema.net/#promociones").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher m = Pattern.compile("2D/3D:\\s*\\$\\s*([\\d.,]+)\\s*/\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m.find()) {
            agregarPrecio(resultado, "Play Cinema", "2D", parsearPrecio(m.group(1)));
            agregarPrecio(resultado, "Play Cinema", "3D", parsearPrecio(m.group(2)));
        }
        return resultado;
    }

    // ===================================================================
    // Tadicor — ALTA confianza (tabla HTML real)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearTadicor() throws Exception {
        Document doc = Jsoup.connect("https://www.cinestadicor.com.ar/DynamicPages?id_section=34").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Tadicor usa formato de número AMERICANO (coma = miles, punto =
        // decimal — ej. "$12,000.00") a diferencia de todas las demás
        // fuentes, que usan formato latino ("$12.000"). Por eso no usa el
        // parsearPrecio() genérico — asumía formato latino y convertía
        // $12,000.00 en $1.200.000 por error (confirmado con datos reales).
        for (var tabla : doc.select("table")) {
            for (var fila : tabla.select("tr")) {
                var celdas = fila.select("td, th");
                if (celdas.size() < 2) continue;
                String etiqueta = celdas.get(0).text();
                String precioTxt = celdas.get(celdas.size() - 1).text();
                Matcher mp = Pattern.compile("\\$\\s*([\\d,]+\\.\\d{2})").matcher(precioTxt);
                if (!mp.find()) continue;
                double precio = Double.parseDouble(mp.group(1).replace(",", ""));
                String formato = etiqueta.toUpperCase().contains("3D") ? "3D" : "2D";
                resultado.computeIfAbsent("Tadicor", k -> new LinkedHashMap<>()).putIfAbsent(formato, precio);
            }
        }
        return resultado;
    }

    // ===================================================================
    // Cines Santa Rosa (La Pampa) — una sola lista de precios para las dos
    // salas de la empresa, Amadeus (Coronel Gil 457) y Milenium (Escalante
    // 270), según su propia página de "Salas". La web no distingue precio
    // por sala, así que ambas reciben la misma lista. Publica además
    // "LUNES 50% OFF: $6000" y "MARTES DE DESCUENTO: $9000" sin decir el
    // formato: se aplican a 2D (el lunes es la mitad de la general 2D) y no a
    // 3D, del que no dice nada. Mayores de 65 y ciclos especiales tampoco
    // dicen formato: van al detalle por categoría de 2D y 3D.
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearSantaRosa() throws Exception {
        Document doc = Jsoup.connect("https://www.cinesantarosa.com.ar/DynamicPages?id_section=34").userAgent(UA).timeout(20000).get();
        return parsearSantaRosa(doc.text());
    }

    Map<String, Map<String, Double>> parsearSantaRosa(String texto) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        Matcher m2d = Pattern.compile("GENERAL\\s*2D[\\s\\S]{0,40}?\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(texto);
        Matcher m3d = Pattern.compile("GENERAL\\s*3D[\\s\\S]{0,40}?\\$\\s*([\\d.,]+)", Pattern.CASE_INSENSITIVE).matcher(texto);
        Double precio2d = m2d.find() ? parsearPrecio(m2d.group(1)) : null;
        Double precio3d = m3d.find() ? parsearPrecio(m3d.group(1)) : null;

        Double precioLunes = precioTrasEtiqueta(texto, "LUNES\\s*50\\s*%\\s*OFF");
        Double precioMartes = precioTrasEtiqueta(texto, "MARTES\\s+DE\\s+DESCUENTO");
        Double mayores65 = precioTrasEtiqueta(texto, "ENTRADA\\s+MAYOR\\s+DE\\s*\\+?\\s*65\\s+A[ÑN]OS");
        Double ciclos = precioTrasEtiqueta(texto, "CICLOS\\s+ESPECIALES");

        for (String sala : List.of("Amadeus", "Milenium")) {
            if (precio2d != null) {
                agregarPrecio(resultado, sala, "2D", precio2d);
                registrarDetalle("Santa Rosa", sala, "2D", "GENERAL", precio2d);
            }
            if (precio3d != null) {
                agregarPrecio(resultado, sala, "3D", precio3d);
                registrarDetalle("Santa Rosa", sala, "3D", "GENERAL", precio3d);
            }
            for (String formato : List.of("2D", "3D")) {
                if (mayores65 != null) registrarDetalle("Santa Rosa", sala, formato, "MAYORES_65", mayores65);
                if (ciclos != null) registrarDetalle("Santa Rosa", sala, formato, "CICLOS_ESPECIALES", ciclos);
            }

            if (precioLunes != null) {
                registrarReducida("Santa Rosa", sala, "2D",
                        new TarifaReducida(precioLunes, EnumSet.of(DayOfWeek.MONDAY), null));
                registrarDetalle("Santa Rosa", sala, "2D", "LUNES_50_OFF", precioLunes);
            }
            if (precioMartes != null) {
                registrarReducida("Santa Rosa", sala, "2D",
                        new TarifaReducida(precioMartes, EnumSet.of(DayOfWeek.TUESDAY), null));
                registrarDetalle("Santa Rosa", sala, "2D", "MARTES_DE_DESCUENTO", precioMartes);
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Santa Rosa: no se encontró el precio general — la página puede haber cambiado de formato");
        }
        return resultado;
    }

    // Precio que sigue a una etiqueta del tipo "LUNES 50% OFF: $ 6000".
    private Double precioTrasEtiqueta(String texto, String etiquetaRegex) {
        Matcher m = Pattern.compile(etiquetaRegex + "\\s*:\\s*\\$\\s*([\\d.,]+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(texto);
        return m.find() ? parsearPrecio(m.group(1)) : null;
    }

    // ===================================================================
    // Showcase — el precio ya NO vive en el HTML de /precios (esa página
    // trae los <div> vacíos, Showcase los llena con JS en el navegador).
    // La fuente real es un archivo JS estático, actualizado por cron,
    // con los 4 niveles de precio (general/matinee/niños/promo) por
    // sucursal y tipo de sala — más rico que lo que teníamos antes, y
    // sin el problema de matching de texto libre que tenía el parser
    // viejo (acá las claves de sucursal ya vienen limpias).
    // ===================================================================
    private static final Map<String, String> SHOWCASE_SUCURSALES = new LinkedHashMap<>() {{
        put("belgrano", "Belgrano");
        put("norcenter", "Norcenter");
        put("haedo", "Haedo");
        put("quilmes", "Quilmes");
        put("cordoba", "Córdoba Villa Cabrera");
        put("allende", "Villa Allende");
        put("rosario", "Rosario");
        // El sitio la presenta como sucursal propia (no como un formato
        // más de Norcenter) — la tratamos igual acá. La variable fuente
        // se llama distinto a las demás (ImaxPrices, no minúscula) pero
        // el patrón de búsqueda "let {var} = [" no cambia.
        put("ImaxPrices", "IMAX Theatre (Norcenter)");
    }};

    private Map<String, Map<String, Double>> scrapearShowcase() throws Exception {
        // .execute().body() en vez de .get(): este archivo es JS plano,
        // no HTML — si Jsoup lo parsea como Document, interpreta los
        // "<b>$16.000</b>" que vienen DENTRO de los strings de precio
        // como tags HTML reales y rompe el texto que necesitamos leer.
        String texto = Jsoup.connect("https://www.todoshowcase.com/cron/data.js")
                .userAgent(UA).timeout(20000).ignoreContentType(true)
                .execute().body();
        return parsearShowcase(texto);
    }

    Map<String, Map<String, Double>> parsearShowcase(String texto) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        for (var entry : SHOWCASE_SUCURSALES.entrySet()) {
            String varName = entry.getKey();
            String sucursal = entry.getValue();

            int inicio = texto.indexOf("let " + varName + " = [");
            if (inicio < 0) {
                log.warn("Showcase: la sucursal '{}' ya no aparece en data.js", sucursal);
                continue;
            }
            int fin = texto.indexOf("];", inicio);
            if (fin < 0) continue;
            String bloqueSucursal = texto.substring(inicio, fin);

            // Cada sala (ej. "Salas 2d", "Salas 2d Superseat") arranca
            // con su "title:" — se parte el bloque ahí para aislar cada
            // sub-objeto antes de buscar sus precios.
            String[] porSala = bloqueSucursal.split("(?=title:\\s*\")");
            for (String bloqueSala : porSala) {
                Matcher mTitulo = Pattern.compile("title:\\s*\"([^\"]+)\"").matcher(bloqueSala);
                if (!mTitulo.find()) continue;

                String tituloCrudo = mTitulo.group(1);
                // "PROMOCIÓN 2x1 con APP MÁS SHOWCASE" (IMAX) no es un
                // formato de sala — es un beneficio de la app de
                // fidelidad, con su propio esquema de precio por persona
                // (no general/matinee/niños/promo como el resto). Se
                // descarta acá en vez de guardarlo con una clave que
                // además trae HTML embebido en el título.
                if (tituloCrudo.toUpperCase().contains("PROMOC")) continue;

                String formato = tituloCrudo.toUpperCase().replace("SALAS ", "").trim();
                Double general = extraerPrecioDeCampo(bloqueSala, "general");
                Double matinee = extraerPrecioDeCampo(bloqueSala, "matinee");
                Double ninos = extraerPrecioDeCampo(bloqueSala, "ninos");
                Double promo = extraerPrecioDeCampo(bloqueSala, "promo");

                if (general != null) {
                    agregarPrecio(resultado, sucursal, formato, general);
                    registrarReducidasShowcase(sucursal, formato, bloqueSala);

                    // Detalle por categoría de esta sala.
                    registrarDetalle("Showcase", sucursal, formato, "GENERAL", general);
                    if (matinee != null) registrarDetalle("Showcase", sucursal, formato, "MATINEE", matinee);
                    if (ninos != null) registrarDetalle("Showcase", sucursal, formato, "MENORES_Y_MAYORES", ninos);
                    if (promo != null) registrarDetalle("Showcase", sucursal, formato, "PROMO", promo);
                }
            }
        }
        return resultado;
    }

    // Tarifas reducidas de una sala, leídas de la etiqueta de cada campo:
    // "Promoción lunes, martes y miércoles... $12.000" (por día) y "MATINEE
    // (todos los días antes de las 15hs)... $12.000" (por horario, cualquier
    // día). "ninos" (menores y mayores de 60) no es por día y no se usa.
    private void registrarReducidasShowcase(String sucursal, String formato, String bloqueSala) {
        Double precioPromo = extraerPrecioDeCampo(bloqueSala, "promo");
        String etiquetaPromo = extraerEtiquetaDeCampo(bloqueSala, "promo");
        if (precioPromo != null && etiquetaPromo != null) {
            registrarReducida("Showcase", sucursal, formato,
                    new TarifaReducida(precioPromo, parsearDias(etiquetaPromo), null));
        }

        Double precioMatinee = extraerPrecioDeCampo(bloqueSala, "matinee");
        String etiquetaMatinee = extraerEtiquetaDeCampo(bloqueSala, "matinee");
        if (precioMatinee != null && etiquetaMatinee != null) {
            Matcher mh = Pattern.compile("(?i)antes de las\\s*(\\d{1,2})").matcher(etiquetaMatinee);
            if (mh.find()) {
                registrarReducida("Showcase", sucursal, formato,
                        new TarifaReducida(precioMatinee, EnumSet.noneOf(DayOfWeek.class),
                                LocalTime.of(Math.min(23, Integer.parseInt(mh.group(1))), 0)));
            }
        }
    }

    private Double extraerPrecioDeCampo(String bloque, String campo) {
        Matcher m = Pattern.compile(campo + "\\s*:\\s*\"[^\"]*?<b>\\$\\s*([\\d.,]+)").matcher(bloque);
        return m.find() ? parsearPrecio(m.group(1)) : null;
    }

    // Texto de la etiqueta de un campo, hasta los puntos suspensivos: de
    // promo: "Promoción lunes, martes y miércoles... <b>$12.000</b>" → "Promoción
    // lunes, martes y miércoles".
    private String extraerEtiquetaDeCampo(String bloque, String campo) {
        Matcher m = Pattern.compile(campo + "\\s*:\\s*\"([^\"]*?)\\.\\.\\.").matcher(bloque);
        return m.find() ? m.group(1) : null;
    }

    // ===================================================================
    // Cinépolis — ALTA confianza (tablas HTML reales confirmadas). Cada
    // complejo es un encabezado "Cinépolis {Nombre}" seguido de tablas
    // ("Salas tradicionales", "Salas 4D", "Salas Monster Screen", "Salas Gold
    // Class"). Las filas "Entrada ..." son la tarifa regular y las filas
    // "Lunes a Miércoles 50%" / "Todos los días 50%" la reducida. Cada tipo
    // de sala se guarda con la clave con que agendadecine etiqueta sus
    // funciones: "2D", "3D", "4D 2D", "4D 3D", "2D MONSTER SCREEN", "3D MONSTER
    // SCREEN" y "GOLD CLASS". "4D" sigue guardándose (con el precio del 4D en
    // 2D) para las demás búsquedas genéricas.
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinepolis() throws Exception {
        Document doc = Jsoup.connect("https://www.cinepolis.com.ar/index.php/precios").userAgent(UA).timeout(20000).get();
        return parsearCinepolis(doc);
    }

    Map<String, Map<String, Double>> parsearCinepolis(Document doc) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();
        Pattern patronPrecio = Pattern.compile("\\$\\s*([\\d.,]+)");

        String sucursalActual = null;
        boolean bloqueFechado = false;

        for (Element el : doc.select("h1, h2, h3, h4, table")) {
            if (!el.tagName().equals("table")) {
                String texto = el.text().trim();
                if (texto.matches("(?i)^Cin[eé]polis\\s.*")) {
                    // Cada complejo es un encabezado "Cinépolis {Nombre}".
                    sucursalActual = texto.replaceFirst("(?i)^Cin[eé]polis\\s*", "").trim();
                    bloqueFechado = false;
                } else if (texto.matches("(?i)^Precios del\\s.*")) {
                    // Bloque de tarifas de fechas ya pasadas ("Precios del 17
                    // al 21 de junio inclusive") que la página deja publicado
                    // debajo de cada complejo: se ignora hasta el próximo.
                    bloqueFechado = true;
                }
                continue;
            }
            if (sucursalActual == null || sucursalActual.isBlank() || bloqueFechado) continue;

            List<Element> filas = el.select("tr");
            if (filas.isEmpty()) continue;
            // Tipo de tabla según el encabezado de su primera fila.
            String encabezado = normalizar(filas.get(0).select("td, th").text());
            boolean esTradicional = encabezado.contains("tradicional");
            boolean es4D = encabezado.contains("4d");
            boolean esMonster = encabezado.contains("monster");
            boolean esGold = encabezado.contains("gold");

            for (Element fila : filas) {
                List<Element> celdas = fila.select("td, th");
                if (celdas.size() < 2) continue;
                Matcher mp = patronPrecio.matcher(celdas.get(celdas.size() - 1).text());
                if (!mp.find()) continue;
                double precio = parsearPrecio(mp.group(1));

                String etiqueta = celdas.get(0).text();
                String upper = etiqueta.toUpperCase();
                String etiquetaNorm = normalizar(etiqueta);
                String base = upper.contains("3D") ? "3D" : "2D";

                // Claves con que se guarda esta fila según el tipo de sala.
                List<String> claves = new ArrayList<>();
                if (esTradicional) {
                    claves.add(base);
                } else if (es4D) {
                    claves.add("4D " + base);
                    if (base.equals("2D")) claves.add("4D");
                } else if (esMonster) {
                    claves.add(base + " MONSTER SCREEN");
                } else if (esGold) {
                    claves.add("GOLD CLASS");
                }
                if (claves.isEmpty()) continue;

                boolean esReducida = etiquetaNorm.contains("lunes") || etiquetaNorm.contains("martes")
                        || etiquetaNorm.contains("todos los dias");
                if (esReducida) {
                    for (String clave : claves) {
                        registrarReducida("Cinépolis", sucursalActual, clave,
                                new TarifaReducida(precio, parsearDias(etiqueta), null));
                    }
                    continue;
                }

                // Tarifa regular: las filas "Entrada ..." (y la fila de Gold
                // Class). Se ignoran "Niños y Mayores de 65".
                boolean esEntrada = upper.startsWith("ENTRADA") || (esGold && etiquetaNorm.contains("gold"));
                if (!esEntrada) continue;
                Map<String, Double> porFormato = resultado.computeIfAbsent(sucursalActual, k -> new LinkedHashMap<>());
                for (String clave : claves) porFormato.putIfAbsent(clave, precio);
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Cinépolis: 0 complejos parseados — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // ===================================================================
    // Cinema Devoto — MEJOR ESFUERZO, verificar contra HTML real
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinemaDevoto() throws Exception {
        Document doc = Jsoup.connect("https://cinemadevoto.com.ar/precios/").userAgent(UA).timeout(20000).get();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Tabla real (confirmada con HTML): fila "GENERAL (SÁBADOS,
        // DOMINGOS Y JUEVES)" trae, en ese orden, el precio 2D con
        // impuestos primero, luego "Precio sin impuestos...", luego el
        // 3D con impuestos, luego su "sin impuestos" — se toma el primer
        // "$" de cada mitad (2D y 3D), ignorando los "sin impuestos".
        var tablas = doc.select("table");
        if (tablas.isEmpty()) return resultado;

        for (var fila : tablas.first().select("tr")) {
            String etiqueta = fila.text();
            if (!etiqueta.toUpperCase().contains("GENERAL")) continue;

            Matcher m = Pattern.compile("\\$\\s*([\\d.,]+)").matcher(etiqueta);
            List<Double> precios = new ArrayList<>();
            while (m.find()) precios.add(parsearPrecio(m.group(1)));

            // precios[0] = 2D con impuestos, precios[1] = 2D sin
            // impuestos, precios[2] = 3D con impuestos, precios[3] = 3D
            // sin impuestos — se usa el "con impuestos" real de cada uno.
            if (precios.size() >= 1) agregarPrecio(resultado, "Cinema Devoto", "2D", precios.get(0));
            if (precios.size() >= 3) agregarPrecio(resultado, "Cinema Devoto", "3D", precios.get(2));
        }

        return resultado;
    }

    // ===================================================================
    // Gran Rex — DESACTIVADO (ver actualizarPrecios): el precio que
    // publica su web parece desactualizado.
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearGranRex() throws Exception {
        Document doc = Jsoup.connect("http://cinesgranrex.com.ar/promociones").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // TEMPORAL — diagnóstico: este parser era "mejor esfuerzo" desde
        // el inicio, nunca confirmado contra HTML real.
        System.out.println("[precios-granrex] Longitud del texto: " + texto.length());
        int idx2d = texto.indexOf("2D");
        if (idx2d >= 0) {
            System.out.println("[precios-granrex] Contexto alrededor de '2D': >>>"
                    + texto.substring(Math.max(0, idx2d - 50), Math.min(texto.length(), idx2d + 250)) + "<<<");
        } else {
            System.out.println("[precios-granrex] '2D' NO aparece en el texto en absoluto");
        }
        System.out.println("[precios-granrex] Cantidad de <table>: " + doc.select("table").size());

        Matcher m2d = Pattern.compile("Precios\\s*2D[\\s\\S]{0,120}?Entrada general\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m2d.find()) agregarPrecio(resultado, "Gran Rex", "2D", parsearPrecio(m2d.group(1)));

        Matcher m3d = Pattern.compile("Precios\\s*3D[\\s\\S]{0,120}?Entrada general\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m3d.find()) agregarPrecio(resultado, "Gran Rex", "3D", parsearPrecio(m3d.group(1)));

        return resultado;
    }

    // ===================================================================
    // Cines del Solar — ALTA confianza (vía sitio del shopping, texto exacto)
    // ===================================================================
    private Map<String, Map<String, Double>> scrapearCinesDelSolar() throws Exception {
        Document doc = Jsoup.connect("https://solardelcerro.com/").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Se toma el precio de fin de semana (Jueves a Domingo) como
        // referencia — es el que más se acerca al concepto de "General"
        // que usamos en el resto de las cadenas.
        Matcher m = Pattern.compile("Jueves, Viernes, S[aá]bados, Domingos y feriados:[\\s\\S]{0,60}?2D\\s*\\$\\s*([\\d.,]+)[\\s\\S]{0,20}?3D\\s*\\$\\s*([\\d.,]+)").matcher(texto);
        if (m.find()) {
            agregarPrecio(resultado, "Cines del Solar", "2D", parsearPrecio(m.group(1)));
            agregarPrecio(resultado, "Cines del Solar", "3D", parsearPrecio(m.group(2)));
        }

        return resultado;
    }

    // ===================================================================
    // Cinema Adrogué — ALTA confianza (tabla HTML real). La página tiene una
    // tabla por tipo de sala ("2D · Salas estándar", "3D · Salas 3D") con la
    // entrada General y dos tarifas por día: "Jue a Dom y Feriados
    // (Promoción)" y "Lunes a Miércoles (Mitad de precio · No válido los
    // lunes o martes feriados)". Los días y la aclaración de feriados se leen
    // de las etiquetas. El cargo por servicio web ($1.200) no entra en el
    // precio de la entrada.
    //
    // Desde Java este sitio falla con "PKIX path building failed" aunque los
    // navegadores lo abren bien: lo más probable es que el servidor mande su
    // certificado sin el intermedio (los navegadores lo buscan solos, Java
    // no). Si es así, se guarda el certificado intermedio en el classpath
    // (ver CERTIFICADO_ADROGUE) y se confía en él SOLO para este sitio, sin
    // desactivar la verificación de seguridad. Si el archivo no existe, la
    // conexión se hace como siempre.
    // ===================================================================
    private static final String CERTIFICADO_ADROGUE = "/certs/cinemaadrogue-intermediate.pem";

    private Map<String, Map<String, Double>> scrapearCinemaAdrogue() throws Exception {
        var conexion = Jsoup.connect("https://www.cinemaadrogue.com/DynamicPages?id_section=28")
                .userAgent(UA).timeout(20000);
        javax.net.ssl.SSLSocketFactory conCertificadoExtra = fabricaSslConCertificado(CERTIFICADO_ADROGUE);
        if (conCertificadoExtra != null) conexion.sslSocketFactory(conCertificadoExtra);
        return parsearCinemaAdrogue(conexion.get());
    }

    Map<String, Map<String, Double>> parsearCinemaAdrogue(Document doc) {
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();
        Pattern patronPrecio = Pattern.compile("\\$\\s*([\\d.,]+)");
        Pattern patronSinFeriado = Pattern.compile("(?i)no v[aá]lidos?\\s+los\\s+(.+?)\\s+feriados");

        String formatoActual = null;
        for (Element el : doc.select("h3, h4, table")) {
            if (!el.tagName().equals("table")) {
                // "2D · Salas estándar" / "3D · Salas 3D".
                Matcher mf = Pattern.compile("(?i)^(2D|3D)\\b").matcher(el.text().trim());
                formatoActual = mf.find() ? mf.group(1).toUpperCase() : null;
                continue;
            }
            if (formatoActual == null) continue;

            for (Element fila : el.select("tr")) {
                List<Element> celdas = fila.select("td, th");
                if (celdas.size() < 2) continue;
                Matcher mp = patronPrecio.matcher(celdas.get(celdas.size() - 1).text());
                if (!mp.find()) continue;
                double precio = parsearPrecio(mp.group(1));
                String etiqueta = celdas.get(0).text().trim();
                String etiquetaNorm = normalizar(etiqueta);

                if (etiquetaNorm.startsWith("general")) {
                    agregarPrecio(resultado, "Cinema Adrogué", formatoActual, precio);
                } else if (etiquetaNorm.startsWith("lunes")) {
                    // "Lunes a Miércoles (Mitad de precio · No válido los lunes
                    // o martes feriados)".
                    TarifaReducida regla = new TarifaReducida(precio, parsearDias(etiqueta), null);
                    Matcher msf = patronSinFeriado.matcher(etiqueta);
                    if (msf.find()) regla = regla.conSinFeriadoEn(parsearDias(msf.group(1)));
                    registrarReducida("Cinema Adrogué", "Cinema Adrogué", formatoActual, regla);
                } else if (etiquetaNorm.startsWith("jue")) {
                    // "Jue a Dom y Feriados (Promoción)": también vale en feriados.
                    TarifaReducida regla = new TarifaReducida(precio, parsearDias(etiqueta), null);
                    if (etiquetaNorm.contains("feriados")) regla = regla.conEnFeriados();
                    registrarReducida("Cinema Adrogué", "Cinema Adrogué", formatoActual, regla);
                }
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Cinema Adrogué: 0 precios parseados — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // Fábrica de conexiones SSL que confía en los certificados de siempre MÁS el
    // certificado (PEM) del classpath. null si ese archivo no existe: en ese
    // caso se usa la configuración normal de Java. Nunca desactiva la
    // verificación: solo suma un certificado concreto a los de confianza.
    private javax.net.ssl.SSLSocketFactory fabricaSslConCertificado(String recurso) throws Exception {
        try (java.io.InputStream in = CarteleraPreciosService.class.getResourceAsStream(recurso)) {
            if (in == null) return null;

            java.security.KeyStore almacen = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            almacen.load(null, null);

            // Los certificados de confianza que Java ya trae.
            javax.net.ssl.TrustManagerFactory porDefecto =
                    javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            porDefecto.init((java.security.KeyStore) null);
            int n = 0;
            for (javax.net.ssl.TrustManager tm : porDefecto.getTrustManagers()) {
                if (tm instanceof javax.net.ssl.X509TrustManager x509) {
                    for (java.security.cert.X509Certificate ca : x509.getAcceptedIssuers()) {
                        almacen.setCertificateEntry("java-" + (n++), ca);
                    }
                }
            }

            // El certificado extra (puede traer más de uno en el mismo archivo).
            java.security.cert.CertificateFactory fabrica = java.security.cert.CertificateFactory.getInstance("X.509");
            for (java.security.cert.Certificate cert : fabrica.generateCertificates(in)) {
                almacen.setCertificateEntry("extra-" + (n++), cert);
            }

            javax.net.ssl.TrustManagerFactory conExtra =
                    javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            conExtra.init(almacen);
            javax.net.ssl.SSLContext contexto = javax.net.ssl.SSLContext.getInstance("TLS");
            contexto.init(null, conExtra.getTrustManagers(), null);
            return contexto.getSocketFactory();
        }
    }

    // ===================================================================
    // Cinema La Plata — 5 cines de La Plata en una sola página
    // (cinemalaplata.com/Home/Precios). Por cada cine y tipo de sala
    // (HD = 2D, 3D, 4D, ATMOS) publica DOS tarifas: la regular ("Jueves a
    // Domingo funciones desde 13 hs") y la reducida ("Lunes a Miércoles
    // todo el día y Jueves a Domingo antes de las 13 hs"). Se guardan las
    // dos, a precio de boletería: el online sale más barato con una
    // promoción sujeta a disponibilidad, más un cargo de servicio.
    // ===================================================================
    private static final Pattern CINEMA_LA_PLATA_CINE = Pattern.compile(
            "([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ0-9 ]*?)\\s+DESDE\\s+\\d{2}/\\d{2}/\\d{4}");
    private static final Pattern CINEMA_LA_PLATA_SALA = Pattern.compile(
            "Sala\\s+(HD|3D|4D|ATMOS)\\s*-\\s*(.*?)\\s+Compra Online:.*?En Boleter[ií]as:\\s*General\\s*\\$\\s*([\\d.,]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private Map<String, Map<String, Double>> scrapearCinemaLaPlata() throws Exception {
        Document doc = Jsoup.connect("https://www.cinemalaplata.com/Home/Precios").userAgent(UA).timeout(20000).get();
        String texto = doc.text();
        Map<String, Map<String, Double>> resultado = new LinkedHashMap<>();

        // Cada cine arranca con "{NOMBRE EN MAYÚSCULAS} DESDE dd/MM/yyyy".
        List<String> nombres = new ArrayList<>();
        List<Integer> inicios = new ArrayList<>();
        List<Integer> finales = new ArrayList<>();
        Matcher cine = CINEMA_LA_PLATA_CINE.matcher(texto);
        while (cine.find()) {
            nombres.add(aTitulo(cine.group(1)));
            inicios.add(cine.start());
            finales.add(cine.end());
        }

        for (int i = 0; i < nombres.size(); i++) {
            int hasta = (i + 1 < nombres.size()) ? inicios.get(i + 1) : texto.length();
            Matcher sala = CINEMA_LA_PLATA_SALA.matcher(texto.substring(finales.get(i), hasta));
            while (sala.find()) {
                String tipo = sala.group(1).toUpperCase();
                String formato = tipo.equals("HD") ? "2D" : tipo;
                String descripcion = sala.group(2);
                double precio = parsearPrecio(sala.group(3));

                if (descripcion.toLowerCase().startsWith("jueves")) {
                    // Tarifa regular (jueves a domingo desde las 13 hs).
                    agregarPrecio(resultado, nombres.get(i), formato, precio);
                } else if (descripcion.toLowerCase().startsWith("lunes")) {
                    // Reducida: "Lunes a Miércoles todo el día, y Jueves a
                    // Domingo funciones anteriores a las 13 hs". Los días y la
                    // hora de corte se leen de esa misma frase.
                    Set<DayOfWeek> dias = parsearDias(descripcion.split("(?i)todo el")[0]);
                    Matcher mh = Pattern.compile("(?i)anteriores a las\\s*(\\d{1,2})").matcher(descripcion);
                    LocalTime antes = mh.find() ? LocalTime.of(Math.min(23, Integer.parseInt(mh.group(1))), 0) : null;
                    registrarReducida("Cinema La Plata", nombres.get(i), formato,
                            new TarifaReducida(precio, dias, antes));
                }
            }
        }

        if (resultado.isEmpty()) {
            log.warn("Cinema La Plata: 0 cines parseados — la página puede haber cambiado de estructura");
        }
        return resultado;
    }

    // "CINEMA SAN MARTIN" → "Cinema San Martin"
    private String aTitulo(String s) {
        StringBuilder sb = new StringBuilder();
        for (String palabra : s.trim().toLowerCase().split("\\s+")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
        }
        return sb.toString();
    }

    public Map<String, Map<String, Map<String, Double>>> obtenerCachePreciosPorCadena() {
        return cachePrecios;
    }
}
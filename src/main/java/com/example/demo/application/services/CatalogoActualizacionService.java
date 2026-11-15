package com.example.demo.application.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * El botón «Actualizar ahora» del catálogo automático de misiones rotativas. Renovar el catálogo tarda un par de minutos (son cientos de pedidos a TMDB),
 * así que se hace en segundo plano: el panel lo dispara, recibe «iniciada» enseguida y consulta el estado cada tanto. Nunca corren dos a la vez.
 * (Si hay dos servidores, la base además deja pasar solo a uno: ver RotacionRepositorio.)
 */
@Service
public class CatalogoActualizacionService {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    /** Cómo está la última actualización. corriendo = hay una en marcha ahora; resultado = el resumen de lo que hizo; errores = lo que no se pudo. */
    public record Estado(boolean corriendo, Long iniciadaPor, LocalDateTime inicio, LocalDateTime fin, String resultado, List<String> errores) {}

    private final RotacionCatalogoService catalogo;
    private final Supplier<LocalDateTime> reloj;
    private final Executor ejecutor;
    private volatile Estado estado = new Estado(false, null, null, null, null, List.of());

    @Autowired
    public CatalogoActualizacionService(RotacionCatalogoService catalogo) {
        this(catalogo, () -> LocalDateTime.now(AR), Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "catalogo-actualizacion"); t.setDaemon(true); return t; }));
    }

    /** Para las pruebas: con reloj y ejecutor propios. */
    CatalogoActualizacionService(RotacionCatalogoService catalogo, Supplier<LocalDateTime> reloj, Executor ejecutor) {
        this.catalogo = catalogo;
        this.reloj = reloj;
        this.ejecutor = ejecutor;
    }

    /** false si ya hay una actualización en marcha (no se pisan). */
    public boolean iniciar(long adminId) {
        synchronized (this) {
            if (estado.corriendo()) return false;
            estado = new Estado(true, adminId, reloj.get(), null, null, List.of());
        }
        try {
            ejecutor.execute(() -> ejecutar(adminId));
        } catch (RuntimeException e) {                                      // el ejecutor no la aceptó: se deja libre para reintentar
            estado = new Estado(false, adminId, estado.inicio(), reloj.get(), null, List.of("No se pudo iniciar: " + e.getMessage()));
            return false;
        }
        return true;
    }

    public Estado estado() { return estado; }

    private void ejecutar(long adminId) {
        LocalDateTime inicio = estado.inicio();
        try {
            RotacionCatalogoService.Resumen r = catalogo.refrescar();
            estado = new Estado(false, adminId, inicio, reloj.get(), r.texto(), new ArrayList<>(r.errores()));
        } catch (Throwable e) {                                              // pase lo que pase, el estado vuelve a «libre»
            estado = new Estado(false, adminId, inicio, reloj.get(), null, List.of("Falló la actualización: " + e.getMessage()));
        }
    }
}

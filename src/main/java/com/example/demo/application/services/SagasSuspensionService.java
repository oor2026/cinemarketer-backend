package com.example.demo.application.services;

import com.example.demo.domain.mision.MisionDatos;
import com.example.demo.domain.mision.MisionDatos.SelloIncompleto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Sello de saga SUSPENDIDO: cuando una saga suma una película y el usuario que ya tenía el sello todavía no la votó, el sello se suspende,
 * la misión vuelve a quedar «falta 1» y al votar lo que falta recupera el sello con la fecha nueva.
 *
 * La regla es una sola y no depende de cuándo se sumó la película: quien tiene el sello de una saga tiene que haber votado TODAS las películas
 * que la saga tiene hoy en sagas.json. Por eso la suspensión no es automática: primero se mira la vista previa (cuántos usuarios y de qué sagas)
 * y recién después el admin la aplica. Se puede aplicar las veces que haga falta: solo toca a quienes siguen sin tener todas votadas.
 */
@Service
public class SagasSuspensionService {

    private static final Logger log = LoggerFactory.getLogger(SagasSuspensionService.class);

    private final MisionDatos datos;
    private final MisionService misiones;

    @Autowired
    public SagasSuspensionService(MisionDatos datos, MisionService misiones) {
        this.datos = datos;
        this.misiones = misiones;
    }

    /** Qué pasaría si se aplicara hoy: por saga, cuántos usuarios perderían (suspendido) el sello y cuántas películas les faltan. No cambia nada. */
    public Map<String, Object> vistaPrevia() {
        List<SelloIncompleto> todos = datos.sellosDeSagaIncompletos();
        Map<Long, List<SelloIncompleto>> porMision = new LinkedHashMap<>();
        for (SelloIncompleto s : todos) porMision.computeIfAbsent(s.misionId(), k -> new ArrayList<>()).add(s);
        List<Map<String, Object>> sagas = new ArrayList<>();
        for (List<SelloIncompleto> lista : porMision.values()) {
            SelloIncompleto p = lista.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("misionId", p.misionId());
            m.put("sagaId", p.sagaId());
            m.put("sello", p.sello());
            m.put("meta", p.meta());
            m.put("usuarios", lista.size());
            Map<Integer, Integer> faltan = new TreeMap<>();                      // cuántas películas les faltan -> cuántos usuarios
            for (SelloIncompleto s : lista) faltan.merge(s.meta() - s.votadas(), 1, Integer::sum);
            m.put("usuariosPorPeliculasQueFaltan", faltan);
            sagas.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sellosASuspender", todos.size());
        r.put("sagas", sagas);
        return r;
    }

    /** Suspende todos los sellos de la vista previa. Cada usuario va en su propia transacción: si uno falla, los demás siguen. */
    public Map<String, Object> aplicar() {
        int ok = 0, fallidos = 0;
        List<String> errores = new ArrayList<>();
        for (SelloIncompleto s : datos.sellosDeSagaIncompletos()) {
            try {
                if (misiones.suspenderSelloDeSaga(s.userId(), s.selloId(), s.misionId(), s.nivel(), s.votadas(), s.meta())) ok++;
            } catch (Exception e) {
                fallidos++;
                if (errores.size() < 20) errores.add("usuario " + s.userId() + ", misión " + s.misionId() + ": " + e.getMessage());
                log.warn("[sagas] no se pudo suspender el sello {} del usuario {}: {}", s.selloId(), s.userId(), e.toString());
            }
        }
        log.info("[sagas] sellos suspendidos: {} (fallaron {})", ok, fallidos);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("suspendidos", ok);
        r.put("fallidos", fallidos);
        if (!errores.isEmpty()) r.put("errores", errores);
        return r;
    }
}

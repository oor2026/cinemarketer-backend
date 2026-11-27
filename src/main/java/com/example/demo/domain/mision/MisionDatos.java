package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionLogica.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Todo lo que el servicio necesita de la base. La implementación real es MisionRepositorio (SQL); las pruebas usan una en memoria. */
public interface MisionDatos {
    /** Las misiones activas que le corresponden a este usuario: las comunes y, de las rotativas, solo las que tiene asignadas. */
    List<Mision> misionesActivas(long userId);
    Map<Long, Integer> nivelesDeSellos(long userId);
    List<Fila> filas(long userId, String cicloDia, String cicloSemana);
    Set<LocalDate> diasActivos(long userId, LocalDate desde);
    void marcarDia(long userId, LocalDate dia);
    /** Devuelve la fila del usuario en esa misión y ciclo, creándola si no existe, y la deja bloqueada hasta cerrar la transacción. */
    Fila filaParaActualizar(long userId, long misionId, String ciclo, LocalDateTime ahora);
    /** true si el título todavía no estaba contado en esa fila. */
    boolean contarTitulo(long filaId, long targetId);
    void actualizarProgreso(long filaId, int progreso, LocalDateTime ahora);
    /** Guarda el contador permanente de una escalera: avance, escalones logrados y veces cumplida. */
    void actualizarContador(long filaId, int progreso, int nivel, int veces, LocalDateTime ahora);
    /** true si la fila estaba EN_CURSO y pasó a CUMPLIDA (false si ya lo estaba: no se otorga dos veces). */
    boolean cumplir(long filaId, int progreso, LocalDateTime ahora);
    void otorgarSello(long userId, long selloId, int nivel, long misionId, LocalDateTime ahora);
    /** Deja pendiente una celebración (tipo NIVEL) o un cartelito liviano (tipo LIVIANA). Devuelve su id. */
    long crearCelebracion(long userId, long misionId, int nivel, String tipo, LocalDateTime ahora);
    /** Celebraciones y cartelitos que el usuario todavía no vio, del más viejo al más nuevo. */
    List<Cumplida> cumplidasSinVer(long userId);
    boolean marcarVisto(long userId, long cumplidaId, LocalDateTime ahora);
    List<SelloGanado> sellosGanados(long userId, int limite);

    // ---- rotación personal (saga, actor/actriz, director, género)
    /** Todos los objetivos del usuario, en cualquier estado. */
    List<Asignacion> asignaciones(long userId);
    /** Los objetivos del catálogo de esa familia que todavía se le pueden ofrecer: activos, no excluidos, no asignados antes y de los que no tiene el sello. */
    List<Candidato> candidatos(long userId, String familia);
    /** Le asigna un objetivo (ACTUAL o SIGUIENTE). No hace nada si el usuario ya tiene uno en ese estado o ya tuvo ese objetivo. */
    void asignar(long userId, String familia, long objetivoId, String estado, LocalDateTime ahora);
    void cambiarEstadoObjetivo(long userId, long objetivoId, String estado, LocalDateTime ahora);
    /** Saca un objetivo DESCARTADO del historial del usuario (para que vuelva a poder sortearse). */
    void quitarAsignacion(long userId, long objetivoId);
    int porDescubrir(long userId);

    // ---- sagas (universo de sagas.json, tabla saga_pelicula)
    /**
     * Cuántas películas DISTINTAS de esa saga votó el usuario (votos vigentes). -1 si la saga todavía no tiene su universo guardado
     * o si esta implementación no lo sabe: entonces la misión cuenta como antes.
     */
    default int votadasDeSaga(long userId, long sagaId) { return -1; }

    /** Los ids de las sagas que ya tienen su universo de películas guardado (saga_pelicula). Las que todavía no, siguen con el conteo anterior. */
    default Set<Long> sagasConUniverso() { return Set.of(); }

    /** Por cada saga con universo guardado, cuántas películas distintas votó el usuario (solo las que votó al menos una). */
    default Map<Long, Integer> votadasPorSaga(long userId) { return Map.of(); }

    /** Un sello de saga que alguien tiene y para el que todavía le faltan películas por votar. */
    record SelloIncompleto(long userId, long selloId, long misionId, long sagaId, String sello, int nivel, LocalDateTime ganadoAt, int meta, int votadas) {}

    /** Todos los sellos de saga que tienen usuarios con menos películas votadas que las que pide hoy la saga. */
    default List<SelloIncompleto> sellosDeSagaIncompletos() { return List.of(); }

    /**
     * Suspende el sello: lo archiva y lo saca de los sellos del usuario, y reabre su misión con el avance real. true si lo tenía.
     * No toca la rotación (de eso se ocupa MisionService).
     */
    default boolean suspenderSelloDeSaga(long userId, long selloId, long misionId, int nivel, int votadas, int meta, LocalDateTime ahora) {
        throw new UnsupportedOperationException();
    }
}
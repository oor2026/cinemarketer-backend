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
}
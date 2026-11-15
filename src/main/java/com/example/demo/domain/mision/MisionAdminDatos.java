package com.example.demo.domain.mision;

import com.example.demo.domain.mision.MisionAdminValidador.MisionRef;
import com.example.demo.domain.mision.MisionAdminValidador.Normalizada;
import com.example.demo.domain.mision.MisionAdminValidador.SelloRef;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Todo lo que el panel de admin necesita de la base. La implementación real es MisionAdminRepositorio (SQL); las pruebas usan una en memoria. */
public interface MisionAdminDatos {

    /**
     * Una misión tal como la ve el admin: sus datos, su sello y sus números. usuariosEnCurso = quiénes están avanzando ahora · usuariosCumplieron = quiénes tienen su sello ·
     * conProgreso = alguien ya hizo algo (por eso se bloquea lo estructural) · misionesDelSello = cuántas misiones comparten su sello.
     */
    record Fila(long id, String titulo, String descripcion, String descripcionCumplida, String categoria, String metrica, int meta, String unidad, String vigencia,
                String tipoContenido, Long generoId, Long directorId, Long sagaId, Long actorId, boolean distintas, long selloId, int nivelSello, double esfuerzo,
                Long prerrequisitoId, Integer sellosNecesarios, String visibilidad, LocalDateTime desde, LocalDateTime hasta, String accionTexto, String accionDestino,
                int orden, String escalera, Integer escaleraPaso, String escaleraSobre, String audiencia, String origen, boolean activa, LocalDateTime publicadaAt,
                LocalDateTime creadaAt, Long creadaPor, LocalDateTime actualizadaAt, Long actualizadaPor,
                String selloNombre, String selloDescripcion, String selloImagenUrl, String selloCategoria,
                int usuariosEnCurso, int usuariosCumplieron, boolean conProgreso, int misionesDelSello) {}

    /** Todos opcionales. estado: BORRADOR, ACTIVA, PAUSADA, PROGRAMADA o VENCIDA. */
    record Filtros(String q, String estado, String origen, String categoria, String metrica, String vigencia) {}

    record HistorialItem(long id, Long usuarioId, String accion, String detalle, LocalDateTime creadaAt) {}

    record SelloItem(long id, String nombre, String categoria, String imagenUrl, int misiones, int usuarios) {}

    /** Un objetivo del catálogo automático con cuántos usuarios lo tienen, lo completaron o lo descartaron. */
    record ObjetivoFila(long id, String familia, long tmdbId, String nombre, int meta, boolean activo, boolean excluido, boolean misionActiva, Double popularidad,
                        LocalDateTime actualizadoAt, long misionId, int asignados, int completados, int descartados) {}

    record ResumenFamilia(String familia, int total, int excluidos, LocalDateTime ultimaActualizacion) {}

    // ---- misiones
    List<Fila> listar(Filtros filtros, String cicloDia, String cicloSemana, LocalDateTime ahora);
    Optional<Fila> obtener(long id, String cicloDia, String cicloSemana);
    /** Crea la misión (y su sello, si es nuevo) como BORRADOR. Devuelve el id de la misión. */
    long crear(Normalizada n, long adminId, LocalDateTime ahora);
    /** Guarda los cambios. retocarSello = además de asignarle el sello, actualiza su nombre, descripción, categoría e imagen (solo si es el sello propio de la misión). */
    void actualizar(long id, Normalizada n, boolean retocarSello, long adminId, LocalDateTime ahora);
    /** Publica (activa = true) o pausa. La primera vez que se publica queda registrada su fecha. */
    void cambiarActiva(long id, boolean activa, long adminId, LocalDateTime ahora);
    /** Borra la misión y su sello si quedó suelto. false si no se pudo porque alguien tiene avance. */
    boolean eliminar(long id);
    /** Los títulos de las misiones que tienen a esta como requisito. */
    List<String> dependientes(long id);
    List<SelloItem> sellos();
    List<HistorialItem> historial(long misionId, int limite);
    void registrarHistorial(Long misionId, String titulo, Long adminId, String accion, String detalle);

    // ---- lo que necesita el validador
    boolean tituloExiste(String titulo, Long excluirMisionId);
    boolean selloNombreExiste(String nombre, Long excluirSelloId);
    Optional<SelloRef> sello(long id);
    Optional<MisionRef> mision(long id);
    Double esfuerzoMinimoActivas(Long excluirMisionId, String audiencia);
    /** Toma un candado que se suelta solo al terminar la transacción: así dos admins no pueden crear a la vez dos misiones con el mismo nombre. */
    void serializarCreaciones();

    // ---- catálogo automático
    List<ObjetivoFila> objetivos(String familia, String q, String estado);
    List<ResumenFamilia> resumenCatalogo();
    /** Excluye (o restaura) un objetivo. retirar = además se lo saca a los usuarios que ya lo tienen asignado. false si no existe. */
    boolean cambiarExclusion(long objetivoId, boolean excluido, boolean retirar, long adminId, LocalDateTime ahora);
}

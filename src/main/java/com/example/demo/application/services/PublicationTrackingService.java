package com.example.demo.application.services;

import com.example.demo.domain.publication.*;
import com.example.demo.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registro de las métricas que alimentan el Panel de seguimiento de los Creators:
 * visualizaciones, clics en compartir e intentos bloqueados por el filtro de palabras.
 * Todo es "mejor esfuerzo": un fallo acá nunca debe romper la acción principal del usuario.
 */
@Service
public class PublicationTrackingService {

    private static final Logger log = LoggerFactory.getLogger(PublicationTrackingService.class);

    // El "día" de una visualización es el día argentino, sin importar la zona del servidor
    // (producción corre en UTC, el entorno local en hora argentina).
    private static final ZoneId ZONA_AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final int MAX_IDS_POR_LOTE = 30;
    // viewer_key = "a:" + clave anónima, y la columna admite 64 caracteres.
    private static final Pattern CLAVE_ANONIMA = Pattern.compile("^[A-Za-z0-9-]{8,40}$");

    private final PublicationRepository publicationRepository;
    private final PublicationViewRepository viewRepository;
    private final PublicationShareClickRepository shareClickRepository;
    private final ModerationBlockEventRepository blockEventRepository;
    private final TransactionTemplate transaccionNueva;

    public PublicationTrackingService(PublicationRepository publicationRepository,
                                      PublicationViewRepository viewRepository,
                                      PublicationShareClickRepository shareClickRepository,
                                      ModerationBlockEventRepository blockEventRepository,
                                      PlatformTransactionManager transactionManager) {
        this.publicationRepository = publicationRepository;
        this.viewRepository = viewRepository;
        this.shareClickRepository = shareClickRepository;
        this.blockEventRepository = blockEventRepository;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private boolean visiblePublicamente(Publication p) {
        return !p.isHidden() && p.getModerationStatus() == PublicationModerationStatus.APPROVED;
    }

    /** Visualizaciones desde el feed de Comunidad (usuario logueado). Las del propio autor no cuentan. */
    @Transactional
    public void registrarVistasFeed(User espectador, List<Long> publicationIds) {
        if (espectador == null || publicationIds == null || publicationIds.isEmpty()) return;
        List<Long> ids = publicationIds.stream().filter(Objects::nonNull).distinct().limit(MAX_IDS_POR_LOTE).toList();
        LocalDate hoy = LocalDate.now(ZONA_AR);
        LocalDateTime ahora = LocalDateTime.now();
        String clave = "u:" + espectador.getId();
        for (Publication p : publicationRepository.findAllById(ids)) {
            if (!visiblePublicamente(p)) continue;
            if (p.getUser().getId().equals(espectador.getId())) continue;
            viewRepository.registrar(p.getId(), clave, "FEED", hoy, ahora);
        }
    }

    /** Visualización de la página pública (/publicacion?id=). Anónima: cuenta como visualización, no como alcance. */
    @Transactional
    public void registrarVistaPublica(Long publicationId, String claveAnonima) {
        if (publicationId == null || claveAnonima == null || !CLAVE_ANONIMA.matcher(claveAnonima).matches()) return;
        publicationRepository.findById(publicationId)
                .filter(this::visiblePublicamente)
                .ifPresent(p -> viewRepository.registrar(
                        p.getId(), "a:" + claveAnonima, "PUBLIC", LocalDate.now(ZONA_AR), LocalDateTime.now()));
    }

    /** Clic en "Compartir". El autor compartiendo lo suyo no cuenta. */
    @Transactional
    public void registrarClicCompartir(User usuario, Long publicationId) {
        if (usuario == null || publicationId == null) return;
        publicationRepository.findById(publicationId)
                .filter(this::visiblePublicamente)
                .filter(p -> !p.getUser().getId().equals(usuario.getId()))
                .ifPresent(p -> {
                    PublicationShareClick clic = new PublicationShareClick();
                    clic.setPublicationId(p.getId());
                    clic.setUserId(usuario.getId());
                    shareClickRepository.save(clic);
                });
    }

    /**
     * Un intento bloqueado por el filtro de palabras. Va en una transacción PROPIA: quien lo llama
     * está a punto de lanzar una excepción que revierte su transacción, y sin esto el registro se
     * perdería con ella. Nunca lanza: si falla, solo lo deja en el log.
     */
    public void registrarBloqueo(Long userId, String origen, String campo) {
        try {
            transaccionNueva.executeWithoutResult(status -> {
                ModerationBlockEvent e = new ModerationBlockEvent();
                e.setUserId(userId);
                e.setOrigin(origen);
                e.setBlockedField(campo);
                blockEventRepository.save(e);
            });
        } catch (Exception ex) {
            log.warn("No se pudo registrar el bloqueo de moderación (userId={}): {}", userId, ex.getMessage());
        }
    }
}

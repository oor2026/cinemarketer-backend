package com.example.demo.infrastructure.scheduler;

import com.example.demo.application.services.CloudflareStreamService;
import com.example.demo.application.services.PublicationService;
import com.example.demo.domain.publication.Publication;
import com.example.demo.domain.publication.PublicationModerationStatus;
import com.example.demo.domain.publication.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VideoModerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(VideoModerationScheduler.class);

    private final PublicationRepository publicationRepository;
    private final CloudflareStreamService cloudflareStreamService;
    private final PublicationService publicationService;

    public VideoModerationScheduler(PublicationRepository publicationRepository,
                                    CloudflareStreamService cloudflareStreamService,
                                    PublicationService publicationService) {
        this.publicationRepository = publicationRepository;
        this.cloudflareStreamService = cloudflareStreamService;
        this.publicationService = publicationService;
    }

    // Cada 20 segundos — no cron porque necesitamos alta frecuencia, no un horario fijo
    @Scheduled(fixedRate = 20000)
    public void procesarVideosEnEspera() {
        // videoModerationStatus cubre los dos casos: video nacido con la
        // publicación (Caso A, donde moderationStatus también es PROCESSING)
        // y video agregado por edición sobre una publicación ya APPROVED (Caso B).
        List<Publication> enProceso = publicationRepository
                .findByVideoModerationStatus(PublicationModerationStatus.PROCESSING);

        if (enProceso.isEmpty()) return;

        log.info("🎬 Revisando {} video(s) en procesamiento...", enProceso.size());

        for (Publication pub : enProceso) {
            if (pub.getVideoUid() == null) continue;

            try {
                CloudflareStreamService.VideoEstado estado =
                        cloudflareStreamService.consultarEstado(pub.getVideoUid());

                if (!estado.listo()) continue; // sigue encodeando, reintentamos en la próxima pasada

                if (estado.duracionSegundos() > PublicationService.MAX_VIDEO_DURATION_SECONDS) {
                    publicationService.rechazarVideoPorDuracion(pub.getId());
                    log.info("⛔ Video de publicación #{} rechazado por duración ({}s > {}s límite).",
                            pub.getId(), estado.duracionSegundos(), PublicationService.MAX_VIDEO_DURATION_SECONDS);
                    continue;
                }

                String[] frameUrls = cloudflareStreamService
                        .obtenerFramesUrls(pub.getVideoUid(), estado.duracionSegundos());
                String urlReproduccion = cloudflareStreamService
                        .obtenerUrlReproduccion(pub.getVideoUid());

                publicationService.resolverVideoProcesado(pub.getId(), frameUrls, urlReproduccion);
                log.info("✅ Video de publicación #{} resuelto.", pub.getId());

            } catch (Exception e) {
                // No abortar el resto del batch por una publicación que falló —
                // simplemente queda en PROCESSING y se reintenta en 20 segundos.
                log.warn("⚠️ Error procesando video de publicación #{}: {}", pub.getId(), e.getMessage());
            }
        }
    }
}
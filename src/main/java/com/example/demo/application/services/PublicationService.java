package com.example.demo.application.services;

import com.example.demo.domain.publication.*;
import com.example.demo.application.dtos.CreatePublicationRequest;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.point.PointAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final PublicationReactionRepository reactionRepository;
    private final PublicationCommentRepository commentRepository;
    private final PublicationReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PointTransactionService pointTransactionService;
    private final BannedWordService bannedWordService;
    private final NotificationService notificationService;

    private final PublicationCommentReactionRepository commentReactionRepository;

    private final com.example.demo.domain.support.SupportTicketRepository supportTicketRepository;
    private final com.example.demo.domain.support.SupportMessageRepository supportMessageRepository;

    private final com.example.demo.domain.moderation.ImageModerationRepository imageModerationRepository;
    private final ImageModerationService imageModerationService;
    private final CloudflareStreamService cloudflareStreamService;

    private final HashtagService hashtagService;
    private final com.example.demo.infrastructure.external.tmdb.TmdbService tmdbService;
    private final com.example.demo.domain.publication.PublicationVotacionOpcionRepository votacionOpcionRepository;
    private final com.example.demo.domain.publication.PublicationVotacionVotoRepository votacionVotoRepository;
    private final com.example.demo.domain.publication.PublicationRankingItemRepository rankingItemRepository;

    public PublicationService(
            PublicationRepository publicationRepository,
            PublicationReactionRepository reactionRepository,
            PublicationCommentRepository commentRepository,
            PublicationReportRepository reportRepository,
            PublicationCommentReactionRepository commentReactionRepository,
            UserRepository userRepository,
            PointTransactionService pointTransactionService,
            BannedWordService bannedWordService,
            NotificationService notificationService,
            com.example.demo.domain.support.SupportTicketRepository supportTicketRepository,
            com.example.demo.domain.support.SupportMessageRepository supportMessageRepository,
            com.example.demo.domain.moderation.ImageModerationRepository imageModerationRepository, ImageModerationService imageModerationService, CloudflareStreamService cloudflareStreamService, HashtagService hashtagService,
            com.example.demo.infrastructure.external.tmdb.TmdbService tmdbService,
            com.example.demo.domain.publication.PublicationVotacionOpcionRepository votacionOpcionRepository,
            com.example.demo.domain.publication.PublicationVotacionVotoRepository votacionVotoRepository,
            com.example.demo.domain.publication.PublicationRankingItemRepository rankingItemRepository) {
        this.publicationRepository = publicationRepository;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.commentReactionRepository = commentReactionRepository;
        this.userRepository = userRepository;
        this.pointTransactionService = pointTransactionService;
        this.bannedWordService = bannedWordService;
        this.notificationService = notificationService;
        this.supportTicketRepository = supportTicketRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.imageModerationRepository = imageModerationRepository;
        this.imageModerationService = imageModerationService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.hashtagService = hashtagService;
        this.tmdbService = tmdbService;
        this.votacionOpcionRepository = votacionOpcionRepository;
        this.votacionVotoRepository = votacionVotoRepository;
        this.rankingItemRepository = rankingItemRepository;
    }

    // Consulta las fechas de estreno de TMDb y confirma que la fecha para el
    // país elegido todavía no haya pasado — Cuenta regresiva de estreno es
    // solo para próximos estrenos, nunca para películas ya estrenadas.
    @SuppressWarnings("unchecked")
    private boolean esFechaDeEstrenoFutura(Long movieId, String countryCode) {
        try {
            Object raw = tmdbService.getReleaseDates(movieId);
            if (!(raw instanceof Map)) return false;
            List<Map<String, Object>> resultados = (List<Map<String, Object>>) ((Map<String, Object>) raw).get("results");
            if (resultados == null) return false;

            for (Map<String, Object> pais : resultados) {
                if (!countryCode.equalsIgnoreCase((String) pais.get("iso_3166_1"))) continue;
                List<Map<String, Object>> fechas = (List<Map<String, Object>>) pais.get("release_dates");
                if (fechas == null || fechas.isEmpty()) return false;
                String fechaStr = (String) fechas.get(0).get("release_date");
                if (fechaStr == null) return false;
                LocalDate fecha = LocalDate.parse(fechaStr.substring(0, 10));
                return fecha.isAfter(LocalDate.now()) || fecha.isEqual(LocalDate.now());
            }
            return false;
        } catch (Exception e) {
            // Si TMDb falla o cambia de forma, no dejamos pasar por defecto —
            // más seguro rechazar la herramienta que arriesgar un countdown roto.
            return false;
        }
    }

    // ==============================================
    // CREAR PUBLICACIÓN
    // ==============================================

    @Transactional
    public Publication createPublication(User user, CreatePublicationRequest req) {

        // Hashtags: se normalizan y validan PRIMERO, antes que cualquier otro
        // control — son texto libre igual que título/contenido, y hoy se
        // colaban sin pasar por el filtro de palabras prohibidas. Si acá
        // bloquea, la publicación ni arranca a evaluarse por lo demás.
        String[] hashtagsNormalizados = normalizarHashtags(req.getHashtags());
        if (hashtagsNormalizados.length > 5) {
            throw new IllegalArgumentException("Podés agregar hasta 5 hashtags por publicación.");
        }
        for (String hashtag : hashtagsNormalizados) {
            if (bannedWordService.analizar(hashtag) == BannedWordService.MatchResult.BLOCK) {
                throw new IllegalArgumentException("Los hashtags contienen palabras no permitidas por nuestras Normas de Convivencia.");
            }
        }

        // Validar título obligatorio
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("El título de la publicación es obligatorio.");
        }
        if (req.getTitle().trim().length() > 150) {
            throw new IllegalArgumentException("El título no puede superar los 150 caracteres.");
        }

        // Validar palabras prohibidas (título y contenido)
        if (bannedWordService.analizar(req.getTitle()) == BannedWordService.MatchResult.BLOCK) {
            throw new IllegalArgumentException("El título contiene palabras no permitidas.");
        }
        BannedWordService.MatchResult moderacion = bannedWordService.analizar(req.getContent());
        if (moderacion == BannedWordService.MatchResult.BLOCK) {
            throw new IllegalArgumentException("El contenido contiene palabras no permitidas.");
        }

        // Techo técnico antiabuso — parejo para todos los planes, no comunicado
        // como límite de producto. Protege infraestructura ante volumen anómalo.
        long todayCount = publicationRepository
                .countTodayPublicationsByUser(user.getId(), LocalDate.now());
        if (todayCount >= User.DAILY_PUBLICATION_ANTIABUSE_LIMIT) {
            throw new IllegalStateException(
                    "Alcanzaste el límite de publicaciones por hoy. Si tu actividad necesita más volumen " +
                            "que esto de forma habitual, escribinos por Contacto.");
        }

        boolean tieneImagen = req.getImageUrls() != null && req.getImageUrls().length > 0;
        boolean tieneVideo = req.getVideoUid() != null && !req.getVideoUid().isBlank();

        // No se pueden combinar imagen y video en la misma publicación (ver nota
        // de diseño: cada formato tiene su propio pipeline de moderación, uno
        // síncrono y otro asíncrono, y mezclarlos abriría un hueco de revisión).
        if (tieneImagen && tieneVideo) {
            throw new IllegalArgumentException("No podés combinar imagen y video en la misma publicación.");
        }
        if ((req.isMovieFichaEnabled() || req.isCountdownEnabled() || req.isVotacionEnabled() || req.isRankingEnabled())
                && (tieneImagen || tieneVideo)) {
            throw new IllegalArgumentException("Esta herramienta no se puede combinar con imagen o video, solo texto.");
        }

        // Validar formato según plan: imagen requiere Premium o Creator, video solo Creator
        if (tieneImagen && !user.puedeSubirImagen()) {
            throw new IllegalArgumentException(
                    "Publicar con imagen es un beneficio Premium o Creator. Con tu plan actual podés publicar solo texto.");
        }
        if (tieneVideo && !user.puedeSubirVideo()) {
            throw new IllegalArgumentException("Publicar con video es un beneficio exclusivo de Creator.");
        }

        // Validar cantidad de imágenes (solo aplica si el plan ya permite imagen)
        int maxImages = user.isActiveCreator() ? 10 : user.isActivePremium() ? 1 : 0;
        if (tieneImagen && req.getImageUrls().length > maxImages) {
            throw new IllegalArgumentException(
                    "Tu plan permite hasta " + maxImages + " imagen(es) por publicación.");
        }

        // Construir entidad
        Publication pub = new Publication();
        pub.setUser(user);
        pub.setAuthorWasCreator(user.isActiveCreator());
        // Solo un Creator puede activar la ficha rica — si un Free/Premium manda
        // el flag en true (bug de cliente o manipulación directa del request),
        // se ignora silenciosamente y cae al link simple de siempre.
        pub.setMovieFichaEnabled(user.isActiveCreator() && req.isMovieFichaEnabled() && req.getMovieId() != null);
        boolean countdownSolicitado = user.isActiveCreator() && req.isCountdownEnabled() && req.getMovieId() != null;
        if (countdownSolicitado) {
            if (req.getCountdownCountryCode() == null || req.getCountdownCountryCode().isBlank()) {
                throw new IllegalArgumentException("Falta elegir el país de estreno para la cuenta regresiva.");
            }
            if (!esFechaDeEstrenoFutura(req.getMovieId(), req.getCountdownCountryCode())) {
                throw new IllegalArgumentException(
                        "La fecha de estreno para el país elegido ya pasó — la cuenta regresiva es solo para próximos estrenos.");
            }
        }
        pub.setCountdownEnabled(countdownSolicitado);
        pub.setCountdownCountryCode(countdownSolicitado ? req.getCountdownCountryCode() : null);

        boolean votacionSolicitada = user.isActiveCreator() && req.isVotacionEnabled() && req.getMovieId() != null
                && req.getOpciones() != null;
        if (votacionSolicitada) {
            long validas = req.getOpciones().stream()
                    .filter(o -> o.getTexto() != null && !o.getTexto().isBlank())
                    .count();
            if (validas < 2 || validas > 5) {
                throw new IllegalArgumentException("Una votación necesita entre 2 y 5 opciones.");
            }
            Integer duracionMin = req.getVotacionDuracionMinutos();
            int maxMinutos = 3 * 30 * 24 * 60; // 3 meses (30 días cada uno) — mismo tope que el frontend
            if (duracionMin == null || duracionMin <= 0 || duracionMin > maxMinutos) {
                throw new IllegalArgumentException("Elegí una duración válida para la votación (entre 1 minuto y 3 meses).");
            }
        }
        pub.setVotacionEnabled(votacionSolicitada);
        pub.setVotacionCierreEn(votacionSolicitada
                ? java.time.LocalDateTime.now().plusMinutes(req.getVotacionDuracionMinutos())
                : null);

        boolean rankingSolicitado = user.isActiveCreator() && req.isRankingEnabled() && req.getRankingItems() != null;
        boolean rankingSegmentada = false;
        if (rankingSolicitado) {
            long validos = req.getRankingItems().stream().filter(i -> i.getMovieId() != null).count();
            if (validos < 3 || validos > 10) {
                throw new IllegalArgumentException("Un ranking necesita entre 3 y 10 películas.");
            }
            if (!"LISTA".equals(req.getRankingFormato()) && !"CARRUSEL".equals(req.getRankingFormato())) {
                throw new IllegalArgumentException("Elegí un formato válido para el ranking.");
            }
            rankingSegmentada = "SEGMENTADA".equals(req.getRankingModoTexto());
            if (!"ESTANDAR".equals(req.getRankingModoTexto()) && !rankingSegmentada) {
                throw new IllegalArgumentException("Elegí un modo de texto válido para el ranking.");
            }
            if (rankingSegmentada) {
                long conTexto = req.getRankingItems().stream()
                        .filter(i -> i.getMovieId() != null && i.getTexto() != null && !i.getTexto().isBlank())
                        .count();
                if (conTexto < validos) {
                    throw new IllegalArgumentException("En modo Segmentada, cada película necesita su propio texto de opinión.");
                }
            }
        }
        pub.setRankingEnabled(rankingSolicitado);
        pub.setRankingFormato(rankingSolicitado ? req.getRankingFormato() : null);
        pub.setRankingModoTexto(rankingSolicitado ? req.getRankingModoTexto() : null);

        pub.setTitle(req.getTitle().trim());
        pub.setHashtags(hashtagsNormalizados);
        pub.setMovieId(req.getMovieId());
        pub.setTerritoryGroup(req.getTerritoryGroup());
        pub.setTerritorySub(req.getTerritorySub());
        pub.setTone(req.getTone());
        // Modo Segmentada: la opinión general vive en cada ítem del ranking,
        // no en el content de la publicación — se guarda vacío en vez de null
        // porque la columna es NOT NULL.
        pub.setContent(rankingSegmentada ? "" : req.getContent());
        pub.setSpoiler(req.isSpoiler());
        pub.setImageUrls(req.getImageUrls());
        pub.setVideoUrl(req.getVideoUrl());

        boolean quedaPendienteRevision = false;

        if (tieneVideo) {
            // Video: el destino de la publicación depende 100% del scheduler
            // asíncrono (VideoModerationScheduler) — no hay nada que evaluar
            // todavía en este punto, el video ni siquiera terminó de subir/encodear.
            pub.setVideoUid(req.getVideoUid());
            pub.setModerationStatus(PublicationModerationStatus.PROCESSING);
            pub.setVideoModerationStatus(PublicationModerationStatus.PROCESSING);
        } else {
            // Capa 2/3 de moderación de imagen: si alguna imagen requiere revisión
            // (combinando el score de NSFWJS con la antigüedad de la cuenta), la
            // publicación nace pendiente en vez de aprobada.
            MotivoRevisionPublicacion motivoImagen = calcularMotivoRevisionPorImagen(user, req.getImageUrls());
            quedaPendienteRevision = motivoImagen != MotivoRevisionPublicacion.NINGUNO;
            if (quedaPendienteRevision) {
                pub.setModerationStatus(PublicationModerationStatus.PENDING_REVIEW);
                pub.setPendingReviewReason(motivoImagen);
            }
        }

        // Calcular puntos: solo suma si el plan del usuario todavía tiene cupo
        // de publicaciones-con-puntos hoy (Free nunca suma; Premium/Creator
        // suman hasta su tope diario, y si ambos están activos, prevalece el mayor).
        long publicacionesConPuntosHoy = publicationRepository
                .countTodayPublicationsConPuntosByUser(user.getId(), LocalDate.now());
        boolean sumaPuntos = publicacionesConPuntosHoy < user.getDailyPublicationsConPuntosLimit();
        // Si suma, el valor depende del plan: Premium 100pts, Free 50pts (mitad,
        // igual patrón que voto/recomendar/comentar). Creator-solo nunca llega
        // acá con sumaPuntos=true, porque su límite es 0.
        int points = sumaPuntos ? (user.isActivePremium() ? 100 : 50) : 0;
        pub.setPointsAwarded(points);

        Publication saved = publicationRepository.save(pub);

        if (votacionSolicitada) {
            int orden = 1;
            for (var o : req.getOpciones()) {
                if (o.getTexto() == null || o.getTexto().isBlank()) continue;
                PublicationVotacionOpcion opcion = new PublicationVotacionOpcion();
                opcion.setPublication(saved);
                opcion.setTexto(o.getTexto().trim());
                opcion.setMovieId(o.getMovieId());
                opcion.setOrden(orden++);
                votacionOpcionRepository.save(opcion);
            }
        }

        if (rankingSolicitado) {
            int orden = 1;
            for (var it : req.getRankingItems()) {
                if (it.getMovieId() == null) continue;
                PublicationRankingItem item = new PublicationRankingItem();
                item.setPublication(saved);
                item.setMovieId(it.getMovieId());
                item.setTexto(it.getTexto() != null ? it.getTexto().trim() : null);
                item.setOrden(orden++);
                rankingItemRepository.save(item);
            }
        }

        hashtagService.incrementar(hashtagsNormalizados);

        // Avisar al autor si quedó pendiente de revisión por su imagen
        if (quedaPendienteRevision) {
            try {
                notificationService.crearPublicacionPendienteRevision(user, saved.getId());
            } catch (Exception ignored) {}
        }

        // Registrar puntos — solo si efectivamente sumó algo. Con points=0,
        // no tiene sentido ni sumar (no cambia nada) ni dejar un registro
        // "-0" en el historial de Mis Puntos, que no aporta información real.
        if (points > 0) {
            user.addAccumulatedPoints(points);
            userRepository.save(user);

            pointTransactionService.registerEarned(
                    user,
                    PointAction.PUBLISH_POST,
                    points,
                    saved.getId(),
                    "Publicación #" + saved.getId() + " en Comunidad"
            );
        }

        return saved;
    }

    /**
     * Cruza el resultado de NSFWJS (guardado al momento de subir cada imagen)
     * con la antigüedad de la cuenta, para decidir si la publicación necesita
     * revisión manual antes de ser visible.
     *
     * - Riesgo ALTO en cualquier imagen -> siempre a revisión, sin importar la cuenta
     * - Riesgo GRIS (dudoso) -> a revisión solo si la cuenta es nueva (< 5 publicaciones con imagen)
     * - Riesgo BAJO -> nunca bloquea
     * - Sin registro de clasificación (no debería pasar, pero por las dudas) -> se trata como GRIS
     */
    private MotivoRevisionPublicacion calcularMotivoRevisionPorImagen(User user, String[] imageUrls) {
        if (imageUrls == null || imageUrls.length == 0) return MotivoRevisionPublicacion.NINGUNO;

        long publicacionesConAdjunto = publicationRepository
                .countPublicacionesConAdjuntoByUserId(user.getId());

        if (publicacionesConAdjunto < 3) {
            return MotivoRevisionPublicacion.CUENTA_NUEVA;
        }

        boolean cuentaNueva = publicacionesConAdjunto < 5;

        for (String url : imageUrls) {
            var registroOpt = imageModerationRepository.findByImageUrl(url);
            com.example.demo.domain.moderation.NivelRiesgoImagen nivel = registroOpt
                    .map(com.example.demo.domain.moderation.ImageModeration::getNivelRiesgo)
                    .orElse(com.example.demo.domain.moderation.NivelRiesgoImagen.GRIS);

            if (nivel == com.example.demo.domain.moderation.NivelRiesgoImagen.ALTO) {
                return MotivoRevisionPublicacion.RIESGO_IMAGEN;
            }
            if (nivel == com.example.demo.domain.moderation.NivelRiesgoImagen.GRIS && cuentaNueva) {
                return MotivoRevisionPublicacion.RIESGO_IMAGEN;
            }
        }
        return MotivoRevisionPublicacion.NINGUNO;
    }

    // Se mantiene para no tener que tocar las 3 llamadas existentes — pero
    // ahora delega en calcularMotivoRevisionPorImagen().
    private boolean requiereRevisionPorImagen(User user, String[] imageUrls) {
        return calcularMotivoRevisionPorImagen(user, imageUrls) != MotivoRevisionPublicacion.NINGUNO;
    }

    // Normaliza hashtags para que "Terror", "terror" y "#Terror" sean lo mismo
    // de cara a una futura agregación por hashtag.
    private String[] normalizarHashtags(String[] raw) {
        if (raw == null) return new String[0];
        return java.util.Arrays.stream(raw)
                .filter(java.util.Objects::nonNull)
                .map(h -> h.trim().toLowerCase())
                .map(h -> h.startsWith("#") ? h.substring(1) : h)
                .map(h -> h.replaceAll("[^a-z0-9áéíóúñ_]", ""))
                .filter(h -> !h.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    // ==============================================
    // FEED
    // ==============================================

    public Page<Publication> getFeed(String territoryGroup, String tone, String hashtag,
                                     String order, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        PublicationModerationStatus approved = PublicationModerationStatus.APPROVED;

        boolean hasTerritory = territoryGroup != null && !territoryGroup.isBlank();
        boolean hasTone = tone != null && !tone.isBlank();
        boolean hasHashtag = hashtag != null && !hashtag.isBlank();

        if ("engagement".equals(order)) {
            return publicationRepository.findByEngagementDesc(pageable);
        }

        if (hasHashtag) {
            String hashtagNormalizado = hashtag.trim().toLowerCase().replaceFirst("^#", "");
            return publicationRepository.findFeedByHashtag(
                    hasTerritory ? territoryGroup : null,
                    hasTone ? tone : null,
                    hashtagNormalizado,
                    pageable);
        }

        if (hasTerritory && hasTone) {
            return publicationRepository
                    .findByHiddenFalseAndModerationStatusAndTerritoryGroupAndToneOrderByCreatedAtDesc(
                            approved, territoryGroup, tone, pageable);
        }
        if (hasTerritory) {
            return publicationRepository
                    .findByHiddenFalseAndModerationStatusAndTerritoryGroupOrderByCreatedAtDesc(
                            approved, territoryGroup, pageable);
        }
        if (hasTone) {
            return publicationRepository
                    .findByHiddenFalseAndModerationStatusAndToneOrderByCreatedAtDesc(
                            approved, tone, pageable);
        }

        return publicationRepository
                .findByHiddenFalseAndModerationStatusOrderByCreatedAtDesc(approved, pageable);
    }

    public Page<Publication> getUserPublications(Long userId, int page, int size) {
        // Mismo criterio que el feed público: oculta Y con moderationStatus
        // APPROVED — así una publicación en PENDING_REVIEW o REJECTED
        // (por ejemplo, un video rechazado en Caso A/B) no aparece acá
        // aunque técnicamente no esté "oculta" en el sentido de hidden=true.
        return publicationRepository.findByUserIdAndHiddenFalseAndModerationStatusOrderByCreatedAtDesc(
                userId, PublicationModerationStatus.APPROVED, PageRequest.of(page, size));
    }

    public Publication getById(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Publicación no encontrada"));
    }

    // ==============================================
    // EDITAR
    // ==============================================

    @Transactional
    public Publication editPublication(User user, Long pubId, String title, String newContent,
                                       String[] hashtags, String[] imageUrls, String videoUid) {
        Publication pub = getById(pubId);

        if (!pub.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo el autor puede editar esta publicación.");
        }
        if (pub.isHidden()) {
            throw new IllegalStateException("No podés editar una publicación oculta.");
        }
        if (bannedWordService.analizar(newContent) == BannedWordService.MatchResult.BLOCK) {
            throw new IllegalArgumentException("El contenido contiene palabras no permitidas.");
        }
        if (title != null && !title.trim().isEmpty()) {
            if (bannedWordService.analizar(title) == BannedWordService.MatchResult.BLOCK) {
                throw new IllegalArgumentException("El título contiene palabras no permitidas.");
            }
            pub.setTitle(title.trim());
        }

        pub.setContent(newContent);
        if (hashtags != null) {
            String[] hashtagsNormalizados = normalizarHashtags(hashtags);
            if (hashtagsNormalizados.length > 5) {
                throw new IllegalArgumentException("Podés agregar hasta 5 hashtags por publicación.");
            }
            hashtagService.ajustarPorEdicion(pub.getHashtags(), hashtagsNormalizados);
            pub.setHashtags(hashtagsNormalizados);
        }
        if (imageUrls != null && imageUrls.length > 0 && pub.isMovieFichaEnabled()) {
            throw new IllegalArgumentException(
                    "Esta publicación usa el modo Ficha técnica y no admite imagen ni video — solo texto.");
        }
        if (videoUid != null && !videoUid.isBlank() && pub.isMovieFichaEnabled()) {
            throw new IllegalArgumentException(
                    "Esta publicación usa el modo Ficha técnica y no admite imagen ni video — solo texto.");
        }

        if (imageUrls != null) {
            // Validar permiso de plan antes de aceptar la nueva imagen —
            // mismo control que en createPublication, para que editar no sea
            // una puerta trasera al límite de formato por plan.
            if (imageUrls.length > 0 && !user.puedeSubirImagen()) {
                throw new IllegalArgumentException(
                        "Publicar con imagen es un beneficio Premium o Creator. Con tu plan actual podés publicar solo texto.");
            }
            int maxImages = user.isActiveCreator() ? 10 : user.isActivePremium() ? 1 : 0;
            if (imageUrls.length > maxImages) {
                throw new IllegalArgumentException(
                        "Tu plan permite hasta " + maxImages + " imagen(es) por publicación.");
            }

            pub.setImageUrls(imageUrls);
            // Si la nueva imagen amerita revisión, la publicación pasa a pendiente
            // (si ya estaba aprobada). No la "des-apruebo" si las imágenes no cambiaron.
            if (requiereRevisionPorImagen(user, imageUrls)) {
                pub.setModerationStatus(PublicationModerationStatus.PENDING_REVIEW);
            }
        }

        // El video solo se puede agregar UNA VEZ por edición, sobre una
        // publicación que todavía no tenía ninguno. Nunca se puede reemplazar
        // un video ya existente — para eso hay que eliminar y republicar.
        if (videoUid != null && !videoUid.isBlank()) {
            if (pub.getVideoUid() != null) {
                throw new IllegalArgumentException(
                        "Esta publicación ya tiene un video y no se puede reemplazar. Si querés cambiarlo, eliminá la publicación y volvé a publicar.");
            }
            if (!user.puedeSubirVideo()) {
                throw new IllegalArgumentException("Publicar con video es un beneficio exclusivo de Creator.");
            }
            if (pub.getImageUrls() != null && pub.getImageUrls().length > 0) {
                throw new IllegalArgumentException("No podés agregar un video a una publicación que ya tiene imagen.");
            }

            pub.setVideoUid(videoUid);
            pub.setVideoModerationStatus(PublicationModerationStatus.PROCESSING);
            // moderationStatus de la publicación NO se toca acá — sigue APPROVED
            // y visible con todo su historial; el video se resuelve por separado
            // en el scheduler (Caso B de resolverVideoProcesado).
        }
        pub.setEditedAt(java.time.LocalDateTime.now());
        return publicationRepository.save(pub);
    }

    // ==============================================
    // OCULTAR
    // ==============================================

    @Transactional
    public void hidePublication(User user, Long pubId) {
        Publication pub = getById(pubId);

        if (!pub.getUser().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new IllegalArgumentException("No tenés permiso para ocultar esta publicación.");
        }

        // Revertir puntos por publicar
        int puntosPublicacion = pub.getPointsAwarded();
        if (puntosPublicacion > 0) {
            if (user.getAvailablePoints() >= puntosPublicacion) {
                user.setAvailablePoints(user.getAvailablePoints() - puntosPublicacion);
            } else if (user.getAccumulatedPoints() >= puntosPublicacion) {
                user.setAccumulatedPoints(user.getAccumulatedPoints() - puntosPublicacion);
            }
            pointTransactionService.registerSpent(user, PointAction.PUBLISH_POST, puntosPublicacion,
                    pubId, "Publicación ocultada #" + pubId);
        }

        // Revertir puntos recibidos por PUNTO (merecés un punto)
        List<PublicationReaction> puntos = reactionRepository
                .findByPublicationIdAndReactionType(pubId, PublicationReactionType.PUNTO);
        for (PublicationReaction r : puntos) {
            if (user.getAvailablePoints() >= 1) {
                user.setAvailablePoints(user.getAvailablePoints() - 1);
            } else if (user.getAccumulatedPoints() >= 1) {
                user.setAccumulatedPoints(user.getAccumulatedPoints() - 1);
            }
            pointTransactionService.registerSpent(user, PointAction.RECEIVE_MERECE_POST, 1,
                    pubId, "Punto revertido por ocultamiento de publicación #" + pubId);
        }

        userRepository.save(user);

        hashtagService.decrementar(pub.getHashtags());
        pub.setHidden(true);
        pub.setHiddenAt(java.time.LocalDateTime.now());
        publicationRepository.save(pub);
    }

    // ==============================================
    // REACCIONES
    // ==============================================

    @Transactional
    public boolean toggleReaction(User user, Long pubId, PublicationReactionType type) {
        Publication pub = getById(pubId);

        // No se puede reaccionar a la propia publicación con PUNTO
        if (type == PublicationReactionType.PUNTO && pub.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("No podés darte un punto a vos mismo.");
        }

        var existing = reactionRepository.findByPublicationIdAndUserIdAndReactionType(
                pubId, user.getId(), type);

        if (existing.isPresent()) {
            PublicationReaction reaction = existing.get();

            // PUNTO es irreversible
            if (type == PublicationReactionType.PUNTO) {
                throw new IllegalStateException("Ya le diste un punto a esta publicación. Esta acción es irreversible.");
            }

            // BANCO: solo toggle, sin notificar
            reaction.setActive(!reaction.isActive());
            reactionRepository.save(reaction);
            return reaction.isActive();
        }

        // Primera vez: crear reacción
        PublicationReaction reaction = new PublicationReaction();
        reaction.setPublication(pub);
        reaction.setUser(user);
        reaction.setReactionType(type);
        reaction.setActive(true);
        reactionRepository.save(reaction);

        // BANCO: notificar solo la primera vez, nunca más
        if (type == PublicationReactionType.BANCO && !pub.getUser().getId().equals(user.getId())) {
            try {
                notificationService.crearBancoPublicacion(
                        pub.getUser(),
                        user.getName(),
                        pubId
                );
            } catch (Exception ignored) {}
        }

        // PUNTO: sumar 1 punto al autor y notificar
        if (type == PublicationReactionType.PUNTO) {
            User author = pub.getUser();
            author.addAccumulatedPoints(1);
            userRepository.save(author);
            pointTransactionService.registerEarned(
                    author,
                    PointAction.RECEIVE_MERECE_POST,
                    1,
                    pubId,
                    "Merecés un punto en publicación #" + pubId
            );
            try {
                notificationService.crearMerecePuntoPublicacion(
                        author,
                        user.getName(),
                        pubId
                );
            } catch (Exception ignored) {}
        }

        return true;
    }

    public long countReactions(Long pubId, PublicationReactionType type) {
        return reactionRepository.countByPublicationIdAndReactionTypeAndActiveTrue(pubId, type);
    }

    public boolean hasReacted(Long userId, Long pubId, PublicationReactionType type) {
        return reactionRepository.existsByPublicationIdAndUserIdAndReactionTypeAndActiveTrue(pubId, userId, type);
    }

    // ==============================================
    // COMENTARIOS
    // ==============================================

    @Transactional
    public PublicationComment addComment(User user, Long pubId, String content, boolean spoiler, Long parentCommentId) {
        Publication pub = getById(pubId);

        if (bannedWordService.analizar(content) == BannedWordService.MatchResult.BLOCK) {
            throw new IllegalArgumentException("El contenido contiene palabras no permitidas.");
        }

        // Si es respuesta, verificar que el padre existe y no está oculto
        Long parentIdFinal = null;
        if (parentCommentId != null) {
            PublicationComment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new RuntimeException("Comentario padre no encontrado"));
            if (parent.isHidden()) {
                throw new IllegalStateException("No podés responder a un comentario oculto.");
            }
            // Aplanar: si el padre ya es una respuesta, usar su padre como raíz
            parentIdFinal = parent.getParentCommentId() != null
                    ? parent.getParentCommentId()
                    : parentCommentId;
        }

        PublicationComment comment = new PublicationComment();
        comment.setPublication(pub);
        comment.setUser(user);
        comment.setContent(content);
        comment.setSpoiler(spoiler);
        comment.setParentCommentId(parentIdFinal);
        PublicationComment saved = commentRepository.save(comment);

        // Notificar según si es comentario o respuesta
        if (parentIdFinal == null) {
            // Comentario raíz — notificar al autor de la publicación
            if (!pub.getUser().getId().equals(user.getId())) {
                try {
                    notificationService.crearComentarioEnPublicacion(
                            pub.getUser(), user.getName(), pubId, saved.getId());
                } catch (Exception ignored) {}
            }
        } else {
            // Respuesta — notificar al autor del comentario padre
            commentRepository.findById(parentIdFinal).ifPresent(padre -> {
                if (!padre.getUser().getId().equals(user.getId())) {
                    try {
                        notificationService.crearRespuestaEnComentarioPublicacion(
                                padre.getUser(), user.getName(), pubId, saved.getId());
                    } catch (Exception ignored) {}
                }
            });
        }

        return saved;
    }

    public Page<PublicationComment> getComments(Long pubId, int page, int size) {
        Page<PublicationComment> comments = commentRepository
                .findByPublicationIdAndHiddenFalseAndParentCommentIdIsNullOrderByCreatedAtAsc(
                        pubId, PageRequest.of(page, size));
        comments.forEach(c -> {
            long replies = commentRepository.findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(c.getId()).size();
            c.setReplyCount(replies);
        });
        return comments;
    }

    public List<PublicationComment> getReplies(Long parentCommentId) {
        return commentRepository.findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(parentCommentId);
    }

    public Page<PublicationComment> getRepliesPaged(Long parentCommentId, int page, int size) {
        return commentRepository.findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(
                parentCommentId, PageRequest.of(page, size));
    }

    // ==============================================
    // REPORTAR
    // ==============================================

    @Transactional
    public void reportPublication(User user, Long pubId, String reason, String description) {
        if (reportRepository.existsByPublicationIdAndUserIdAndTargetType(
                pubId, user.getId(), PublicationReportTargetType.PUBLICATION)) {
            throw new IllegalStateException("Ya reportaste esta publicación.");
        }

        Publication pub = getById(pubId);

        PublicationReport report = new PublicationReport();
        report.setTargetType(PublicationReportTargetType.PUBLICATION);
        report.setPublication(pub);
        report.setUser(user);
        report.setReason(reason);
        report.setDescription(description);
        reportRepository.save(report);

        pub.setReportCount(pub.getReportCount() + 1);
        pub.setAdminReviewed(false);
        publicationRepository.save(pub);
    }

    // ==============================================
    // REPORTAR COMENTARIO
    // ==============================================

    @Transactional
    public void reportComment(User user, Long commentId, String reason, String description) {
        if (reportRepository.existsByPublicationCommentIdAndUserId(commentId, user.getId())) {
            throw new IllegalStateException("Ya reportaste este comentario.");
        }

        PublicationComment comment = getCommentById(commentId);

        PublicationReport report = new PublicationReport();
        report.setTargetType(PublicationReportTargetType.COMMENT);
        report.setPublication(comment.getPublication()); // referencia para mostrar "Publicación ID: X" en el admin
        report.setPublicationComment(comment);
        report.setUser(user);
        report.setReason(reason);
        report.setDescription(description);
        reportRepository.save(report);

        comment.setReportCount(comment.getReportCount() + 1);
        comment.setAdminReviewed(false);

        // Si ya había sido desestimado antes, un reporte nuevo lo vuelve a poner a la vista del admin
        if (comment.getModerationStatus() == PublicationCommentModerationStatus.DISMISSED) {
            comment.setModerationStatus(PublicationCommentModerationStatus.PENDING_REVIEW);
        }

        commentRepository.save(comment);
    }

    // ==============================================
    // LÍMITE DIARIO — INFO
    // ==============================================

    public DailyLimitInfo getDailyLimitInfo(User user) {
        long publicacionesHoy = publicationRepository
                .countTodayPublicationsByUser(user.getId(), LocalDate.now());
        long publicacionesConPuntosHoy = publicationRepository
                .countTodayPublicationsConPuntosByUser(user.getId(), LocalDate.now());
        int limitePuntos = user.getDailyPublicationsConPuntosLimit();
        return new DailyLimitInfo(publicacionesHoy, publicacionesConPuntosHoy, limitePuntos);
    }

    public record DailyLimitInfo(long publicacionesHoy, long publicacionesConPuntosHoy, int limitePuntos) {
        // Siempre se puede publicar (salvo el techo antiabuso, que no se comunica acá)
        public boolean puedeSumarPuntos() {
            return publicacionesConPuntosHoy < limitePuntos;
        }
        public long puntosRestantesHoy() {
            return Math.max(0, limitePuntos - publicacionesConPuntosHoy);
        }
    }

    public PublicationComment getCommentById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));
    }

    public long countComments(Long pubId) {
        return commentRepository.countByPublicationIdAndHiddenFalseAndParentCommentIdIsNull(pubId);
    }

    public boolean hasCommentBanco(Long userId, Long commentId) {
        return commentReactionRepository.findByCommentIdAndUserId(commentId, userId)
                .map(r -> r.isActive())
                .orElse(false);
    }

    public long countCommentBanco(Long commentId) {
        return commentReactionRepository.countByCommentIdAndActiveTrue(commentId);
    }

    @Transactional
    public Map<String, Object> toggleCommentBanco(User user, Long commentId) {
        PublicationComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentario no encontrado"));

        boolean esPropio = comment.getUser().getId().equals(user.getId());

        var existing = commentReactionRepository.findByCommentIdAndUserId(commentId, user.getId());

        boolean added;
        if (existing.isPresent()) {
            PublicationCommentReaction r = existing.get();
            r.setActive(!r.isActive());
            commentReactionRepository.save(r);
            added = r.isActive();
        } else {
            PublicationCommentReaction r = new PublicationCommentReaction();
            r.setComment(comment);
            r.setUser(user);
            r.setActive(true);
            commentReactionRepository.save(r);
            added = true;

            // Notificar solo la primera vez y solo si no es propio
            if (!esPropio) {
                try {
                    notificationService.crearBancoComentarioPublicacion(
                            comment.getUser(),
                            user.getName(),
                            comment.getPublication().getId(),
                            commentId
                    );
                } catch (Exception ignored) {}
            }
        }

        long count = commentReactionRepository.countByCommentIdAndActiveTrue(commentId);
        return Map.of("added", added, "count", count);
    }

    public PublicationComment saveComment(PublicationComment comment) {
        return commentRepository.save(comment);
    }

    @Transactional
    public PublicationComment editComment(User user, Long commentId, String newContent) {
        PublicationComment c = getCommentById(commentId);

        if (!c.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Solo el autor puede editar este comentario.");
        }
        if (bannedWordService.analizar(newContent) == BannedWordService.MatchResult.BLOCK) {
            throw new IllegalArgumentException("El contenido contiene palabras no permitidas.");
        }

        c.setContent(newContent);
        c.setEditedAt(java.time.LocalDateTime.now());
        return commentRepository.save(c);
    }

    // Admin — stats
    public Map<String, Long> getAdminStats() {
        long reportadas = publicationRepository.countByReportCountGreaterThanAndAdminReviewedFalse(0);
        long activas    = publicationRepository.countByHiddenFalseAndModerationStatus(PublicationModerationStatus.APPROVED);
        long ocultas    = publicationRepository.countByHiddenTrue();
        long hoy        = publicationRepository.countTodayPublications();
        long pendientesRevision = publicationRepository.countByModerationStatus(PublicationModerationStatus.PENDING_REVIEW);
        return Map.of("reportadas", reportadas, "activas", activas,
                "ocultas", ocultas, "hoy", hoy, "pendientesRevision", pendientesRevision);
    }

    // Admin — todas
    public Page<Publication> adminGetAll(String territoryGroup, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        if (territoryGroup != null && !territoryGroup.isBlank()) {
            return publicationRepository.findByTerritoryGroupOrderByCreatedAtDesc(territoryGroup, pageable);
        }
        return publicationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // Admin — reportadas
    public Page<Publication> adminGetReported(int page, int size) {
        return publicationRepository.findByReportCountGreaterThanAndAdminReviewedFalse(0, PageRequest.of(page, size));
    }

    // Admin — ocultas
    public Page<Publication> adminGetHidden(int page, int size) {
        return publicationRepository.findByHiddenTrueOrderByHiddenAtDesc(PageRequest.of(page, size));
    }

    // Admin — ocultar
    public void adminHide(Long pubId) {
        Publication pub = getById(pubId);
        pub.setHidden(true);
        pub.setHiddenAt(java.time.LocalDateTime.now());
        pub.setAdminReviewed(true);
        publicationRepository.save(pub);
    }

    // Admin — restaurar
    public void adminRestore(Long pubId) {
        Publication pub = getById(pubId);
        pub.setHidden(false);
        pub.setHiddenAt(null);
        pub.setAdminReviewed(true);
        pub.setModerationStatus(PublicationModerationStatus.APPROVED);
        publicationRepository.save(pub);
    }

    // Admin — sancionar
    public void adminSanction(Long pubId) {
        Publication pub = getById(pubId);
        pub.setHidden(true);
        pub.setHiddenAt(java.time.LocalDateTime.now());
        pub.setAdminReviewed(true);
        pub.setModerationStatus(PublicationModerationStatus.REJECTED);
        publicationRepository.save(pub);
    }

    @Transactional
    public void adminHideWithNotification(Long pubId, String reason) {
        Publication pub = getById(pubId);
        pub.setHidden(true);
        pub.setHiddenAt(java.time.LocalDateTime.now());
        pub.setAdminReviewed(true);

        // Si la publicación (Caso A) o su video (Caso B) estaban en revisión,
        // se cierran como rechazados también al ocultar — sin esto, quedan
        // fantasma para siempre en la cola de Pendientes, aunque ya no sean
        // visibles para nadie.
        if (pub.getModerationStatus() == PublicationModerationStatus.PENDING_REVIEW) {
            pub.setModerationStatus(PublicationModerationStatus.REJECTED);
        }
        if (pub.getVideoUid() != null && pub.getVideoModerationStatus() == PublicationModerationStatus.PENDING_REVIEW) {
            pub.setVideoModerationStatus(PublicationModerationStatus.REJECTED);
        }

        publicationRepository.save(pub);

        // Restar puntos otorgados por la publicación
        User author = pub.getUser();
        int pointsToDeduct = pub.getPointsAwarded();
        if (pointsToDeduct > 0) {
            author.addAccumulatedPoints(-pointsToDeduct);
            userRepository.save(author);
            pointTransactionService.registerEarned(
                    author,
                    PointAction.PUBLICATION_SANCTION,
                    -pointsToDeduct,
                    pubId,
                    "Publicación #" + pubId + " ocultada por moderación: " + reason
            );
        }

        // Notificar al autor
        try {
            notificationService.crearPublicacionOculta(author, reason, pubId, pub.getTitle());
        } catch (Exception ignored) {}
    }

    @Transactional
    public void adminDismissReports(Long pubId) {
        Publication pub = getById(pubId);
        List<PublicationReport> reports = reportRepository
                .findByPublicationIdAndTargetType(pubId, PublicationReportTargetType.PUBLICATION);

        for (PublicationReport report : reports) {
            User reporter = report.getUser();
            try {
                com.example.demo.domain.support.SupportTicket ticket = new com.example.demo.domain.support.SupportTicket();
                ticket.setUser(reporter);
                ticket.setSubject("Revisamos tu reporte");
                ticket.setStatus(com.example.demo.domain.support.TicketStatus.OPEN);
                com.example.demo.domain.support.SupportTicket savedTicket = supportTicketRepository.save(ticket);

                String tituloPub = (pub.getTitle() != null && !pub.getTitle().isBlank())
                        ? "\"" + pub.getTitle() + "\"" : "(sin título)";
                String mensaje = "Hemos revisado la publicación que reportaste, " + tituloPub + ", " +
                        "y la misma no viola ninguno de nuestros reglamentos, política de privacidad ni normas de convivencia. " +
                        "Esperamos que sigas ayudándonos a sanear el contenido con cualquier otra publicación que creas que viola " +
                        "alguna regla de nuestra comunidad. Saludos.";

                com.example.demo.domain.support.SupportMessage msg = new com.example.demo.domain.support.SupportMessage();
                msg.setTicket(savedTicket);
                msg.setSenderType(com.example.demo.domain.support.SenderType.ADMIN);
                msg.setSenderName("Cinemarketer");
                msg.setContent(mensaje);
                msg.setReadByAdmin(true);
                msg.setReadByUser(false);
                supportMessageRepository.save(msg);
            } catch (Exception ignored) {}
        }

        reportRepository.deleteByPublicationIdAndTargetType(pubId, PublicationReportTargetType.PUBLICATION);

        pub.setReportCount(0);
        pub.setAdminReviewed(true);
        publicationRepository.save(pub);
    }

    public Page<Publication> adminGetActive(int page, int size) {
        return publicationRepository
                .findByHiddenFalseAndModerationStatusOrderByCreatedAtDesc(
                        PublicationModerationStatus.APPROVED, PageRequest.of(page, size));
    }

    @Transactional
    public void adminSanctionWithNotification(Long pubId, String reason) {
        Publication pub = getById(pubId);
        pub.setHidden(true);
        pub.setHiddenAt(java.time.LocalDateTime.now());
        pub.setAdminReviewed(true);
        pub.setModerationStatus(PublicationModerationStatus.REJECTED);

        if (pub.getVideoUid() != null && pub.getVideoModerationStatus() == PublicationModerationStatus.PENDING_REVIEW) {
            pub.setVideoModerationStatus(PublicationModerationStatus.REJECTED);
        }

        publicationRepository.save(pub);

        // Restar puntos
        User author = pub.getUser();
        int pointsToDeduct = pub.getPointsAwarded();
        if (pointsToDeduct > 0) {
            author.addAccumulatedPoints(-pointsToDeduct);
            userRepository.save(author);
            pointTransactionService.registerEarned(
                    author,
                    PointAction.PUBLICATION_SANCTION,
                    -pointsToDeduct,
                    pubId,
                    "Publicación #" + pubId + " sancionada por moderación: " + reason
            );
        }

        // Notificar al autor
        try {
            notificationService.crearPublicacionOculta(author, reason + " (sanción)", pubId, pub.getTitle());
        } catch (Exception ignored) {}
    }

    // Admin — pendientes de revisión por imagen (Capa 3 + 4)
    public Page<Map<String, Object>> adminGetPendingReview(int page, int size) {
        Page<Publication> pubs = publicationRepository.findPendingReviewPrioritized(PageRequest.of(page, size));
        return pubs.map(this::mapPublicationParaAdmin);
    }

    // Admin — detalle de una publicación puntual (usado por el modal "Ver" en Pendientes,
    // ya que la vista pública de publicacion.html no muestra contenido PENDING_REVIEW)
    public Map<String, Object> adminGetDetalle(Long pubId) {
        Publication pub = getById(pubId);
        return mapPublicationParaAdmin(pub);
    }

    private boolean esMasRiesgoso(com.example.demo.domain.moderation.NivelRiesgoImagen candidato,
                                  com.example.demo.domain.moderation.NivelRiesgoImagen actual) {
        java.util.List<String> orden = java.util.List.of("BAJO", "GRIS", "ALTO");
        return orden.indexOf(candidato.name()) > orden.indexOf(actual.name());
    }

    // Arma la representación de una publicación para el panel admin, sumando
    // el nivelRiesgo y los scores de image_moderation sin exponer el User completo.
    private Map<String, Object> mapPublicationParaAdmin(Publication pub) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", pub.getId());
        Map<String, Object> userResumen = new java.util.LinkedHashMap<>();
        if (pub.getUser() != null) {
            userResumen.put("id", pub.getUser().getId());
            userResumen.put("name", pub.getUser().getName());
            userResumen.put("email", pub.getUser().getEmail());
        }
        m.put("user", userResumen);
        m.put("territoryGroup", pub.getTerritoryGroup());
        m.put("tone", pub.getTone());
        m.put("content", pub.getContent());
        m.put("movieId", pub.getMovieId());
        m.put("reportCount", pub.getReportCount());
        m.put("createdAt", pub.getCreatedAt());
        m.put("hidden", pub.isHidden());
        m.put("imageUrls", pub.getImageUrls());
        m.put("moderationStatus", pub.getModerationStatus());
        m.put("videoModerationStatus", pub.getVideoModerationStatus());
        // Independientes: pueden estar ambos en true a la vez (Caso A con
        // video riesgoso), o solo uno (Caso B, o Caso A con video limpio).
        // El frontend muestra el set de botones que corresponda a cada uno.
        m.put("pendienteTexto", pub.getModerationStatus() == PublicationModerationStatus.PENDING_REVIEW);
        m.put("pendienteVideo", pub.getVideoModerationStatus() == PublicationModerationStatus.PENDING_REVIEW);
        m.put("pendingReviewReason", pub.getPendingReviewReason());

        m.put("videoUid", pub.getVideoUid());
        m.put("videoUrl", pub.getVideoUrl());
        m.put("videoFrameUrls", pub.getVideoFrameUrls());

        // Junta imagen(es) + frames de video en una sola lista, y muestra el
        // peor nivel de riesgo encontrado entre todos — así el admin ve el
        // mismo tipo de dato sin importar si la publicación es de imagen o video.
        java.util.List<String> mediaUrls = new java.util.ArrayList<>();
        if (pub.getImageUrls() != null) mediaUrls.addAll(java.util.Arrays.asList(pub.getImageUrls()));
        if (pub.getVideoFrameUrls() != null) mediaUrls.addAll(java.util.Arrays.asList(pub.getVideoFrameUrls()));

        // Riesgo individual por cada imagen/frame, para que el admin pueda ver
        // cuál en particular disparó la revisión — no solo el peor agregado.
        java.util.Map<String, Object> imageRiesgos = new java.util.LinkedHashMap<>();
        com.example.demo.domain.moderation.ImageModeration peor = null;
        for (String url : mediaUrls) {
            var opt = imageModerationRepository.findByImageUrl(url);
            if (opt.isEmpty()) continue;
            imageRiesgos.put(url, opt.get().getNivelRiesgo());
            if (peor == null || esMasRiesgoso(opt.get().getNivelRiesgo(), peor.getNivelRiesgo())) {
                peor = opt.get();
            }
        }
        m.put("imageRiesgos", imageRiesgos);
        if (peor != null) {
            m.put("nivelRiesgo", peor.getNivelRiesgo());
            m.put("scorePorn", peor.getScorePorn());
            m.put("scoreSexy", peor.getScoreSexy());
            m.put("scoreHentai", peor.getScoreHentai());
        }

        // Detalle de reportes individuales (usuario, motivo, descripción, fecha)
        java.util.List<java.util.Map<String, Object>> reportesList = new java.util.ArrayList<>();
        java.util.List<PublicationReport> reportes = reportRepository
                .findByPublicationIdAndTargetType(pub.getId(), PublicationReportTargetType.PUBLICATION);
        for (PublicationReport r : reportes) {
            java.util.Map<String, Object> rm = new java.util.LinkedHashMap<>();
            rm.put("usuario", r.getUser() != null ? r.getUser().getName() : "—");
            rm.put("email", r.getUser() != null ? r.getUser().getEmail() : "—");
            rm.put("motivo", r.getReason());
            rm.put("descripcion", r.getDescription());
            rm.put("fecha", r.getCreatedAt());
            reportesList.add(rm);
        }
        m.put("reportes", reportesList);

        return m;
    }

    // Límite de duración de video, para controlar costo de Cloudflare Stream —
    // validado también en frontend, pero esta es la validación real e inevitable.
    public static final int MAX_VIDEO_DURATION_SECONDS = 60;

    // Llamado por VideoModerationScheduler cuando Cloudflare confirma que el
    // video excede el límite de duración. Distingue Caso A (publicación entera
    // dependía del video) de Caso B (video agregado por edición, la publicación
    // ya estaba viva y sigue viva, solo se rechaza el video).
    @Transactional
    public void rechazarVideoPorDuracion(Long pubId) {
        Publication pub = getById(pubId);
        boolean esPublicacionNuevaConVideo = pub.getModerationStatus() == PublicationModerationStatus.PROCESSING;

        pub.setVideoModerationStatus(PublicationModerationStatus.REJECTED);
        if (esPublicacionNuevaConVideo) {
            pub.setModerationStatus(PublicationModerationStatus.REJECTED);
        }
        publicationRepository.save(pub);

        try {
            if (esPublicacionNuevaConVideo) {
                notificationService.crearPublicacionRechazadaPorDuracion(pub.getUser(), pub.getId(), MAX_VIDEO_DURATION_SECONDS);
            } else {
                notificationService.crearVideoRechazadoPorDuracion(pub.getUser(), pub.getId(), MAX_VIDEO_DURATION_SECONDS);
            }
        } catch (Exception ignored) {}
    }

    // Admin — aprobar publicación marcada PENDING_REVIEW por la imagen (falso positivo)
    @Transactional
    public void adminApprovePendingReview(Long pubId) {
        Publication pub = getById(pubId);

        // Guarda contra pantallas de admin desactualizadas: si mientras tanto
        // esta misma publicación ya fue sancionada/ocultada por otra acción
        // más reciente, su estado actual ya no es PENDING_REVIEW — no
        // corresponde aprobarla y resucitar por error algo que ya se rechazó.
        if (pub.getModerationStatus() != PublicationModerationStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Esta publicación ya no está pendiente de revisión (estado actual: " +
                            pub.getModerationStatus() + "). Probablemente ya fue procesada por otra acción.");
        }

        pub.setModerationStatus(PublicationModerationStatus.APPROVED);
        pub.setAdminReviewed(true);
        publicationRepository.save(pub);

        try {
            notificationService.crearPublicacionAprobada(pub.getUser(), pub.getId());
        } catch (Exception ignored) {}
    }

    // Admin — Caso B: aprobar solo el video, sin tocar el resto de la publicación
    @Transactional
    public void adminAprobarVideo(Long pubId) {
        Publication pub = getById(pubId);
        pub.setVideoModerationStatus(PublicationModerationStatus.APPROVED);
        pub.setVideoUrl(cloudflareStreamService.obtenerUrlReproduccion(pub.getVideoUid()));
        publicationRepository.save(pub);

        try {
            notificationService.crearVideoAprobado(pub.getUser(), pub.getId());
        } catch (Exception ignored) {}
    }

    // Admin — Caso B: rechazar solo el video. La publicación (texto, engagement,
    // puntos ya generados) queda intacta — nunca estuvo en riesgo.
    @Transactional
    public void adminRechazarVideo(Long pubId, String reason) {
        Publication pub = getById(pubId);
        pub.setVideoModerationStatus(PublicationModerationStatus.REJECTED);
        publicationRepository.save(pub);

        try {
            notificationService.crearVideoRechazado(pub.getUser(), pub.getId(), reason);
        } catch (Exception ignored) {}
    }

    // Llamado por VideoModerationScheduler cuando Cloudflare confirma que el
    // video terminó de encodear. Clasifica los 5 frames (reutilizando el mismo
    // NSFWJS que usa imagen) y decide APPROVED o PENDING_REVIEW.
    @Transactional
    public void resolverVideoProcesado(Long pubId, String[] frameUrls, String urlReproduccion) {
        Publication pub = getById(pubId);

        // Clasificar cada frame — mismo mecanismo que usa upload-image.
        for (String frameUrl : frameUrls) {
            try {
                imageModerationService.clasificarYGuardar(frameUrl);
            } catch (Exception ignored) {}
        }

        pub.setVideoFrameUrls(frameUrls);
        boolean quedaPendienteRevision = requiereRevisionPorImagen(pub.getUser(), frameUrls);

        // Caso A: la publicación entera nació dependiendo de este video
        // (todavía en PROCESSING) — el resultado del video define también
        // el destino de la publicación completa, como ya funcionaba.
        boolean esPublicacionNuevaConVideo = pub.getModerationStatus() == PublicationModerationStatus.PROCESSING;

        if (quedaPendienteRevision) {
            pub.setVideoModerationStatus(PublicationModerationStatus.PENDING_REVIEW);
            if (esPublicacionNuevaConVideo) {
                pub.setModerationStatus(PublicationModerationStatus.PENDING_REVIEW);
            }
            // videoUrl queda en null a propósito: no se muestra hasta ser aprobado
        } else {
            pub.setVideoModerationStatus(PublicationModerationStatus.APPROVED);
            pub.setVideoUrl(urlReproduccion);
            if (esPublicacionNuevaConVideo) {
                pub.setModerationStatus(PublicationModerationStatus.APPROVED);
            }
            // Caso B con video aprobado: moderationStatus ya era APPROVED, no se toca
        }

        publicationRepository.save(pub);

        try {
            if (esPublicacionNuevaConVideo) {
                // Notificación sobre la publicación completa, como ya existía
                if (quedaPendienteRevision) {
                    notificationService.crearPublicacionPendienteRevision(pub.getUser(), pub.getId());
                } else {
                    notificationService.crearPublicacionAprobada(pub.getUser(), pub.getId());
                }
            } else {
                // Caso B: notificación acotada al video, la publicación ya estaba viva
                if (quedaPendienteRevision) {
                    notificationService.crearVideoPendienteRevision(pub.getUser(), pub.getId());
                } else {
                    notificationService.crearVideoAprobado(pub.getUser(), pub.getId());
                }
            }
        } catch (Exception ignored) {}
    }

    @Transactional
    public void votar(User user, Long publicationId, Long opcionId) {
        Publication pub = getById(publicationId);
        if (pub.getVotacionCierreEn() != null && pub.getVotacionCierreEn().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalStateException("Esta votación ya cerró.");
        }
        if (votacionVotoRepository.existsByPublicationIdAndUserId(publicationId, user.getId())) {
            throw new IllegalStateException("Ya votaste en esta encuesta.");
        }
        PublicationVotacionOpcion opcion = votacionOpcionRepository.findById(opcionId)
                .orElseThrow(() -> new IllegalArgumentException("Opción no encontrada."));
        if (!opcion.getPublication().getId().equals(publicationId)) {
            throw new IllegalArgumentException("La opción no pertenece a esta publicación.");
        }

        PublicationVotacionVoto voto = new PublicationVotacionVoto();
        voto.setPublicationId(publicationId);
        voto.setOpcion(opcion);
        voto.setUser(user);
        votacionVotoRepository.save(voto);
    }

    public Map<String, Object> getVotacionResultado(Long publicationId, Long userId) {
        Publication pub = getById(publicationId);
        List<PublicationVotacionOpcion> opciones = votacionOpcionRepository.findByPublicationIdOrderByOrdenAsc(publicationId);

        boolean cerrada = pub.getVotacionCierreEn() != null && pub.getVotacionCierreEn().isBefore(java.time.LocalDateTime.now());
        boolean yaVoto = userId != null && votacionVotoRepository.existsByPublicationIdAndUserId(publicationId, userId);
        Long opcionElegidaId = userId != null
                ? votacionVotoRepository.findByPublicationIdAndUserId(publicationId, userId)
                .map(v -> v.getOpcion().getId()).orElse(null)
                : null;

        List<Map<String, Object>> opcionesConVotos = new java.util.ArrayList<>();
        long totalVotos = 0;
        for (PublicationVotacionOpcion o : opciones) {
            long votos = votacionVotoRepository.countByOpcionId(o.getId());
            totalVotos += votos;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("texto", o.getTexto());
            m.put("movieId", o.getMovieId());
            m.put("votos", votos);
            opcionesConVotos.add(m);
        }
        for (Map<String, Object> m : opcionesConVotos) {
            long votos = (long) m.get("votos");
            m.put("porcentaje", totalVotos > 0 ? Math.round(votos * 100.0 / totalVotos) : 0);
        }

        Map<String, Object> resultado = new java.util.LinkedHashMap<>();
        resultado.put("opciones", opcionesConVotos);
        resultado.put("totalVotos", totalVotos);
        resultado.put("yaVoto", yaVoto);
        resultado.put("opcionElegidaId", opcionElegidaId);
        resultado.put("cerrada", cerrada);
        resultado.put("cierreEn", pub.getVotacionCierreEn() != null ? pub.getVotacionCierreEn().toString() : null);
        return resultado;
    }

    public List<Map<String, Object>> getRankingItems(Long publicationId) {
        List<PublicationRankingItem> items = rankingItemRepository.findByPublicationIdOrderByOrdenAsc(publicationId);
        List<Map<String, Object>> resultado = new java.util.ArrayList<>();
        for (PublicationRankingItem it : items) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("movieId", it.getMovieId());
            m.put("texto", it.getTexto());
            m.put("orden", it.getOrden());
            resultado.add(m);
        }
        return resultado;
    }
}


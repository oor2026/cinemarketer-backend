package com.example.demo.web.controllers;

import com.example.demo.application.services.PublicationService;
import com.example.demo.domain.publication.Publication;
import com.example.demo.domain.publication.PublicationComment;
import com.example.demo.domain.publication.PublicationModerationStatus;
import com.example.demo.domain.publication.PublicationReactionType;
import com.example.demo.application.dtos.CreatePublicationRequest;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationService publicationService;
    private final UserRepository userRepository;
    private final com.example.demo.application.services.CloudinaryService cloudinaryService;
    private final com.example.demo.application.services.ImageModerationService imageModerationService;
    private final com.example.demo.application.services.CloudflareStreamService cloudflareStreamService;
    private final com.example.demo.application.services.HashtagService hashtagService;

    public PublicationController(PublicationService publicationService,
                                 UserRepository userRepository,
                                 com.example.demo.application.services.CloudinaryService cloudinaryService,
                                 com.example.demo.application.services.ImageModerationService imageModerationService,
                                 com.example.demo.application.services.CloudflareStreamService cloudflareStreamService,
                                 com.example.demo.application.services.HashtagService hashtagService) {
        this.publicationService = publicationService;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
        this.imageModerationService = imageModerationService;
        this.cloudflareStreamService = cloudflareStreamService;
        this.hashtagService = hashtagService;
    }

    private User getUser(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    // GET /api/publications?territoryGroup=&tone=&hashtag=&order=recent&page=0&size=20
    @GetMapping
    public ResponseEntity<Page<Publication>> getFeed(
            @RequestParam(required = false) String territoryGroup,
            @RequestParam(required = false) String tone,
            @RequestParam(required = false) String hashtag,
            @RequestParam(defaultValue = "recent") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                publicationService.getFeed(territoryGroup, tone, hashtag, order, page, size));
    }

    // GET /api/publications/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Publication> getById(@PathVariable Long id) {
        Publication pub = publicationService.getById(id);
        // Este es el endpoint público (usado por el modal de notificaciones y
        // cualquier link directo a una publicación). No debe devolver nada
        // que un usuario común no debería poder ver todavía: ni oculta, ni
        // pendiente/en proceso — recién visible una vez APPROVED. La vista de
        // admin usa un endpoint aparte (/admin/publications/{id}/detalle) que
        // sí puede ver contenido oculto o pendiente, así que esto no la afecta.
        if (pub.isHidden() || pub.getModerationStatus() != PublicationModerationStatus.APPROVED) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pub);
    }

    // GET /api/publications/hashtags/suggest?q=terr
    @GetMapping("/hashtags/suggest")
    public ResponseEntity<List<Map<String, Object>>> suggestHashtags(
            @RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(hashtagService.sugerir(q, 8));
    }

    // POST /api/publications
    @PostMapping
    public ResponseEntity<Publication> create(
            @AuthenticationPrincipal UserDetails ud,
            @RequestBody CreatePublicationRequest req) {
        User user = getUser(ud);
        Publication pub = publicationService.createPublication(user, req);
        return ResponseEntity.ok(pub);
    }

    // PUT /api/publications/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Publication> edit(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User user = getUser(ud);
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String[] imageUrls = null;
        if (body.get("imageUrls") instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) body.get("imageUrls");
            imageUrls = list.stream().map(Object::toString).toArray(String[]::new);
        }
        String[] hashtags = null;
        if (body.get("hashtags") instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) body.get("hashtags");
            hashtags = list.stream().map(Object::toString).toArray(String[]::new);
        }
        String videoUid = (String) body.get("videoUid");
        return ResponseEntity.ok(
                publicationService.editPublication(user, id, title, content, hashtags, imageUrls, videoUid));
    }

    // DELETE /api/publications/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hide(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        User user = getUser(ud);
        publicationService.hidePublication(user, id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/publications/{id}/react?type=BANCO
    @PostMapping("/{id}/react")
    public ResponseEntity<Map<String, Object>> react(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestParam PublicationReactionType type) {
        User user = getUser(ud);
        boolean added = publicationService.toggleReaction(user, id, type);
        long count = publicationService.countReactions(id, type);
        return ResponseEntity.ok(Map.of("added", added, "count", count));
    }

    // GET /api/publications/{id}/comments?page=0&size=20
    @GetMapping("/{id}/comments")
    public ResponseEntity<Page<PublicationComment>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(publicationService.getComments(id, page, size));
    }

    // GET /api/publications/comments/{parentId}/replies?page=0&size=3
    @GetMapping("/comments/{parentId}/replies")
    public ResponseEntity<Map<String, Object>> getReplies(
            @PathVariable Long parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {
        var result = publicationService.getRepliesPaged(parentId, page, size);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "hasMore", !result.isLast(),
                "totalElements", result.getTotalElements()
        ));
    }

    // POST /api/publications/{id}/comments
    @PostMapping("/{id}/comments")
    public ResponseEntity<PublicationComment> addComment(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User user = getUser(ud);
        String content = (String) body.get("content");
        boolean spoiler = Boolean.TRUE.equals(body.get("spoiler"));
        Long parentCommentId = body.get("parentCommentId") != null
                ? Long.valueOf(body.get("parentCommentId").toString()) : null;
        return ResponseEntity.ok(
                publicationService.addComment(user, id, content, spoiler, parentCommentId));
    }

    // POST /api/publications/{id}/report
    @PostMapping("/{id}/report")
    public ResponseEntity<Void> report(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = getUser(ud);
        publicationService.reportPublication(
                user, id, body.get("reason"), body.get("description"));
        return ResponseEntity.ok().build();
    }

    // POST /api/publications/comments/{id}/report
    @PostMapping("/comments/{id}/report")
    public ResponseEntity<Void> reportComment(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = getUser(ud);
        publicationService.reportComment(
                user, id, body.get("reason"), body.get("description"));
        return ResponseEntity.ok().build();
    }

    // GET /api/publications/limit
    @GetMapping("/limit")
    public ResponseEntity<PublicationService.DailyLimitInfo> getDailyLimit(
            @AuthenticationPrincipal UserDetails ud) {
        User user = getUser(ud);
        return ResponseEntity.ok(publicationService.getDailyLimitInfo(user));
    }

    // GET /api/publications/public/{id} — sin autenticación
    @GetMapping("/public/{id}")
    public ResponseEntity<Publication> getPublic(@PathVariable Long id) {
        Publication pub = publicationService.getById(id);
        if (pub.isHidden() || !"APPROVED".equals(pub.getModerationStatus().name())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(pub);
    }

    // GET /api/publications/user/{userId}?page=0&size=20
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<Publication>> getUserPublications(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                publicationService.getUserPublications(userId, page, size));
    }

    // PUT /api/publications/comments/{id}
    @PutMapping("/comments/{id}")
    public ResponseEntity<PublicationComment> editComment(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = getUser(ud);
        return ResponseEntity.ok(
                publicationService.editComment(user, id, body.get("content")));
    }

    // DELETE /api/publications/comments/{id}
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> hideComment(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        User user = getUser(ud);
        PublicationComment c = publicationService.getCommentById(id);
        if (!c.getUser().getId().equals(user.getId()) && !user.isAdmin())
            return ResponseEntity.status(403).build();
        c.setHidden(true);
        publicationService.saveComment(c);
        return ResponseEntity.noContent().build();
    }

    // GET /api/publications/{id}/trivia
    @GetMapping("/{id}/trivia")
    public ResponseEntity<Map<String, Object>> getTrivia(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        Long userId = (ud != null) ? getUser(ud).getId() : null;
        return ResponseEntity.ok(publicationService.getTriviaResultado(id, userId));
    }

    // POST /api/publications/{id}/responder-trivia
    @PostMapping("/{id}/responder-trivia")
    public ResponseEntity<Map<String, Object>> responderTrivia(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        User user = getUser(ud);
        Long opcionId = body.get("opcionId");
        if (opcionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta indicar la opción."));
        }
        try {
            publicationService.responderTrivia(user, id, opcionId);
            return ResponseEntity.ok(publicationService.getTriviaResultado(id, user.getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/publications/{id}/ranking
    @GetMapping("/{id}/ranking")
    public ResponseEntity<List<Map<String, Object>>> getRanking(@PathVariable Long id) {
        return ResponseEntity.ok(publicationService.getRankingItems(id));
    }

    // GET /api/publications/{id}/votacion
    @GetMapping("/{id}/votacion")
    public ResponseEntity<Map<String, Object>> getVotacion(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        Long userId = (ud != null) ? getUser(ud).getId() : null;
        return ResponseEntity.ok(publicationService.getVotacionResultado(id, userId));
    }

    // POST /api/publications/{id}/votar
    @PostMapping("/{id}/votar")
    public ResponseEntity<Map<String, Object>> votar(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        User user = getUser(ud);
        Long opcionId = body.get("opcionId");
        if (opcionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta indicar la opción."));
        }
        try {
            publicationService.votar(user, id, opcionId);
            return ResponseEntity.ok(publicationService.getVotacionResultado(id, user.getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/publications/{id}/reactions/count
    @GetMapping("/{id}/reactions/count")
    public ResponseEntity<Map<String, Long>> getReactionCounts(@PathVariable Long id) {
        long banco = publicationService.countReactions(id, PublicationReactionType.BANCO);
        long punto = publicationService.countReactions(id, PublicationReactionType.PUNTO);
        return ResponseEntity.ok(Map.of("banco", banco, "punto", punto));
    }

    // GET /api/publications/{id}/comments/count
    @GetMapping("/{id}/comments/count")
    public ResponseEntity<Map<String, Long>> getCommentCount(@PathVariable Long id) {
        long count = publicationService.countComments(id);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // POST /api/publications/comments/{id}/banco
    @PostMapping("/comments/{id}/banco")
    public ResponseEntity<Map<String, Object>> bancarComentario(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        User user = getUser(ud);
        return ResponseEntity.ok(publicationService.toggleCommentBanco(user, id));
    }

    // GET /api/publications/comments/{id}/banco
    @GetMapping("/comments/{id}/banco")
    public ResponseEntity<Map<String, Object>> getCommentBanco(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        User user = getUser(ud);
        boolean active = publicationService.hasCommentBanco(user.getId(), id);
        long count = publicationService.countCommentBanco(id);
        return ResponseEntity.ok(Map.of("active", active, "count", count));
    }

    // GET /api/publications/{id}/my-reactions
    @GetMapping("/{id}/my-reactions")
    public ResponseEntity<Map<String, Boolean>> getMyReactions(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        User user = getUser(ud);
        boolean banco = publicationService.hasReacted(user.getId(), id, PublicationReactionType.BANCO);
        boolean punto = publicationService.hasReacted(user.getId(), id, PublicationReactionType.PUNTO);
        return ResponseEntity.ok(Map.of("banco", banco, "punto", punto));
    }

    // POST /api/publications/upload-video
    @PostMapping(value = "/upload-video", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal UserDetails ud) {
        User user = getUser(ud);

        if (!user.puedeSubirVideo()) {
            return ResponseEntity.status(403).body(Map.of("error", "Publicar con video es un beneficio exclusivo de Creator."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
        }
        if (file.getSize() > 25 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "El video supera el tamaño máximo permitido (25MB)."));
        }

        String videoUid;
        try {
            videoUid = cloudflareStreamService.subirVideo(file);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("videoUid", videoUid));
    }

    // POST /api/publications/upload-image
    @PostMapping(value = "/upload-image", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal UserDetails ud) {
        getUser(ud); // verificar autenticación

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo está vacío."));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "La imagen supera el tamaño máximo permitido (5MB)."));
        }
        if (!esImagenReal(file)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "El archivo no es una imagen válida. Solo se aceptan JPEG, PNG, GIF o WEBP."));
        }

        String url;
        try {
            url = cloudinaryService.uploadImage(file, "publications");
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error al subir la imagen"));
        }

        // Clasificar la imagen (Capa 2 de moderación). Fail-closed: si el servicio
        // de moderación no responde, la subida se rechaza — no queda huérfana ni
        // se le devuelve la URL al frontend para usar en una publicación.
        try {
            imageModerationService.clasificarYGuardar(url);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * Valida el tipo real del archivo leyendo sus primeros bytes (magic numbers),
     * en vez de confiar en la extensión o el Content-Type declarado por el cliente
     * (ambos son falsificables por quien arma la request a mano, sin pasar por el
     * selector de archivos del navegador).
     */
    private boolean esImagenReal(org.springframework.web.multipart.MultipartFile file) {
        try {
            byte[] header = new byte[12];
            int leidos;
            try (java.io.InputStream is = file.getInputStream()) {
                leidos = is.read(header);
            }
            if (leidos < 4) return false;

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return true;
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return true;
            }
            // GIF: "GIF8"
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return true;
            }
            // WEBP: "RIFF" + (bytes 8-11) "WEBP"
            if (leidos >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
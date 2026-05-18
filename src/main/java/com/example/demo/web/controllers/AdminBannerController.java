package com.example.demo.web.controllers;

import com.example.demo.application.dtos.BannerDto;
import com.example.demo.application.dtos.BannerRequest;
import com.example.demo.application.services.CloudinaryService;
import com.example.demo.domain.banner.Banner;
import com.example.demo.domain.banner.BannerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/banners")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminBannerController {

    private final BannerRepository bannerRepository;
    private final CloudinaryService cloudinaryService;

    public AdminBannerController(BannerRepository bannerRepository,
                                 CloudinaryService cloudinaryService) {
        this.bannerRepository = bannerRepository;
        this.cloudinaryService = cloudinaryService;
    }

    /**
     * Listar todos los banners
     * GET /api/admin/banners
     */
    @GetMapping
    public ResponseEntity<List<BannerDto>> getAllBanners() {
        List<BannerDto> dtos = bannerRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Crear nuevo banner (sin imagen aún)
     * POST /api/admin/banners
     */
    @PostMapping
    @Transactional
    public ResponseEntity<BannerDto> createBanner(@RequestBody BannerRequest request) {
        Banner banner = new Banner();
        mapRequestToBanner(request, banner);
        bannerRepository.save(banner);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(banner));
    }

    /**
     * Editar datos del banner (link, módulo, posición, visibilidad, marca)
     * PUT /api/admin/banners/{id}
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<BannerDto> updateBanner(@PathVariable Long id,
                                                  @RequestBody BannerRequest request) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner no encontrado"));
        mapRequestToBanner(request, banner);
        bannerRepository.save(banner);
        return ResponseEntity.ok(toDto(banner));
    }

    /**
     * Subir o reemplazar imagen del banner — valida dimensiones máximas 300x1100
     * POST /api/admin/banners/{id}/image
     */
    @PostMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> uploadImage(@PathVariable Long id,
                                         @RequestParam("image") MultipartFile file) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner no encontrado"));

        // Validar tamaño de archivo (máx 2MB)
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La imagen no puede superar los 2MB"));
        }

        // Validar tipo de archivo
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg")
                && !contentType.equals("image/png")
                && !contentType.equals("image/webp")
                && !contentType.equals("image/gif"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Formato no permitido. Usá JPG, PNG, WEBP o GIF"));
        }

        try {
            // Eliminar imagen anterior si existe
            if (banner.getImageUrl() != null) {
                cloudinaryService.deleteImage(banner.getImageUrl());
            }

            // Subir nueva imagen a Cloudinary
            String imageUrl = cloudinaryService.uploadImage(file, "cinemarketer/banners");
            banner.setImageUrl(imageUrl);
            bannerRepository.save(banner);

            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al subir la imagen: " + e.getMessage()));
        }
    }

    /**
     * Eliminar imagen del banner (queda el slot vacío)
     * DELETE /api/admin/banners/{id}/image
     */
    @DeleteMapping("/{id}/image")
    @Transactional
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner no encontrado"));

        if (banner.getImageUrl() != null) {
            try {
                cloudinaryService.deleteImage(banner.getImageUrl());
            } catch (Exception e) {
                // log pero no falla
            }
            banner.setImageUrl(null);
            bannerRepository.save(banner);
        }

        return ResponseEntity.ok(Map.of("message", "Imagen eliminada correctamente"));
    }

    /**
     * Toggle visibilidad (ocultar/mostrar)
     * PATCH /api/admin/banners/{id}/toggle-visible
     */
    @PatchMapping("/{id}/toggle-visible")
    @Transactional
    public ResponseEntity<?> toggleVisible(@PathVariable Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner no encontrado"));

        banner.setVisible(!banner.getVisible());
        bannerRepository.save(banner);

        String estado = banner.getVisible() ? "visible" : "oculto";
        return ResponseEntity.ok(Map.of(
                "message", "Banner ahora " + estado,
                "visible", banner.getVisible()
        ));
    }

    /**
     * Eliminar banner definitivamente
     * DELETE /api/admin/banners/{id}
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteBanner(@PathVariable Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner no encontrado"));

        // Eliminar imagen de Cloudinary si existe
        if (banner.getImageUrl() != null) {
            try {
                cloudinaryService.deleteImage(banner.getImageUrl());
            } catch (Exception e) {
                // log pero no falla
            }
        }

        bannerRepository.delete(banner);
        return ResponseEntity.ok(Map.of("message", "Banner eliminado correctamente"));
    }

    // =============================================
    // HELPERS
    // =============================================
    private void mapRequestToBanner(BannerRequest req, Banner banner) {
        if (req.getLinkDestino() != null)  banner.setLinkDestino(req.getLinkDestino());
        if (req.getModulo() != null)       banner.setModulo(req.getModulo());
        if (req.getPosicion() != null)     banner.setPosicion(req.getPosicion());
        if (req.getVisible() != null)      banner.setVisible(req.getVisible());
        if (req.getNombreMarca() != null)  banner.setNombreMarca(req.getNombreMarca());
    }

    private BannerDto toDto(Banner b) {
        BannerDto dto = new BannerDto();
        dto.setId(b.getId());
        dto.setImageUrl(b.getImageUrl());
        dto.setLinkDestino(b.getLinkDestino());
        dto.setModulo(b.getModulo());
        dto.setPosicion(b.getPosicion());
        dto.setVisible(b.getVisible());
        dto.setNombreMarca(b.getNombreMarca());
        dto.setCreatedAt(b.getCreatedAt());
        dto.setUpdatedAt(b.getUpdatedAt());
        return dto;
    }
}
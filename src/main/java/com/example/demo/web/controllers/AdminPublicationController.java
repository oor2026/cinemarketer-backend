package com.example.demo.web.controllers;

import com.example.demo.application.services.PublicationService;
import com.example.demo.domain.publication.Publication;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/publications")
public class AdminPublicationController {

    private final PublicationService publicationService;

    public AdminPublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(publicationService.getAdminStats());
    }

    @GetMapping
    public ResponseEntity<Page<Publication>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String territoryGroup) {
        return ResponseEntity.ok(publicationService.adminGetAll(territoryGroup, page, size));
    }

    @GetMapping("/reported")
    public ResponseEntity<Page<Publication>> getReported(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(publicationService.adminGetReported(page, size));
    }

    @GetMapping("/hidden")
    public ResponseEntity<Page<Publication>> getHidden(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(publicationService.adminGetHidden(page, size));
    }

    @PostMapping("/{id}/hide")
    public ResponseEntity<Void> hide(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Violación de normas de convivencia") : "Violación de normas de convivencia";
        publicationService.adminHideWithNotification(id, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        publicationService.adminRestore(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/sanction")
    public ResponseEntity<Void> sanction(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Violación grave de normas de convivencia") : "Violación grave de normas de convivencia";
        publicationService.adminSanctionWithNotification(id, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        publicationService.adminDismissReports(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve-pending")
    public ResponseEntity<Void> approvePending(@PathVariable Long id) {
        publicationService.adminApprovePendingReview(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active")
    public ResponseEntity<Page<Publication>> getActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(publicationService.adminGetActive(page, size));
    }

    @GetMapping("/pending-review")
    public ResponseEntity<Page<Map<String, Object>>> getPendingReview(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(publicationService.adminGetPendingReview(page, size));
    }

    @GetMapping("/{id}/detalle")
    public ResponseEntity<Map<String, Object>> getDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(publicationService.adminGetDetalle(id));
    }

    @PostMapping("/{id}/approve-video")
    public ResponseEntity<Void> approveVideo(@PathVariable Long id) {
        publicationService.adminAprobarVideo(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject-video")
    public ResponseEntity<Void> rejectVideo(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "No cumple con nuestras normas de convivencia") : "No cumple con nuestras normas de convivencia";
        publicationService.adminRechazarVideo(id, reason);
        return ResponseEntity.ok().build();
    }
}
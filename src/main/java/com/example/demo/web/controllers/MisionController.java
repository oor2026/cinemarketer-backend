package com.example.demo.web.controllers;

import com.example.demo.application.services.MisionService;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MisionController {

    private final MisionService misionService;
    private final UserRepository userRepository;

    public MisionController(MisionService misionService, UserRepository userRepository) {
        this.misionService = misionService;
        this.userRepository = userRepository;
    }

    private User usuario(UserDetails ud) {
        return ud == null ? null : userRepository.findByEmail(ud.getUsername()).orElse(null);
    }

    /** Liviano: lo pide la tarjeta del feed. */
    @GetMapping("/missions/me/resumen")
    public ResponseEntity<Map<String, Object>> resumen(@AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(misionService.resumen(u.getId()));
    }

    /** Completo: lo pide el modal «Tus misiones». */
    @GetMapping("/missions/me")
    public ResponseEntity<Map<String, Object>> completo(@AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(misionService.completo(u.getId()));
    }

    /** El usuario ya vio la celebración de esa misión cumplida. */
    @PostMapping("/missions/me/cumplidas/{id}/visto")
    public ResponseEntity<Void> visto(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        misionService.visto(u.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /** La ✕ de una misión rotativa: no la vuelve a ver y pasa la que sigue. */
    @PostMapping("/missions/me/{id}/descartar")
    public ResponseEntity<Void> descartar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return misionService.descartar(u.getId(), id) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** Las misiones que el usuario descartó, para que pueda recuperarlas. */
    @GetMapping("/missions/me/descartadas")
    public ResponseEntity<List<Map<String, Object>>> descartadas(@AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(misionService.descartadas(u.getId()));
    }

    /** Recupera una misión descartada. */
    @PostMapping("/missions/me/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        User u = usuario(ud);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return misionService.restaurar(u.getId(), id) ? ResponseEntity.noContent().build() : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /** Mi Sala › Mis logros (el perfil de cualquier usuario es visible para un usuario logueado). */
    @GetMapping("/users/{id}/logros")
    public ResponseEntity<Map<String, Object>> logros(@PathVariable Long id, @AuthenticationPrincipal UserDetails ud) {
        if (usuario(ud) == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(misionService.logros(id));
    }
}
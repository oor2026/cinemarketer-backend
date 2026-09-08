package com.example.demo.infrastructure.security;

import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class DemoAccountWriteBlockFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_BLOQUEADOS = Set.of("POST", "PUT", "PATCH", "DELETE");

    // JSON fijo de 2 campos — no hace falta ObjectMapper para esto, y
    // evita el lío de versiones de Jackson (Spring Boot 4 trae la
    // nueva tools.jackson.* por defecto, pero alguna otra dependencia
    // del proyecto —Cloudinary, probablemente— trae la vieja
    // com.fasterxml.jackson.*, y solo hay un bean ObjectMapper
    // registrado, del tipo nuevo).
    private static final String CUERPO_RESPUESTA =
            "{\"error\":\"DEMO_ACCOUNT\",\"message\":\"Esta es una cuenta de demostración — no podés interactuar (votar, comentar, etc.).\"}";

    private final UserRepository userRepository;

    public DemoAccountWriteBlockFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (METODOS_BLOQUEADOS.contains(request.getMethod())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                User user = userRepository.findByEmail(auth.getName()).orElse(null);
                if (user != null && user.isDemo()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(CUERPO_RESPUESTA);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
package com.example.demo.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Log de entrada
        System.out.println("🔍 [JwtFilter] Procesando: " + request.getMethod() + " " + request.getRequestURI());

        // Manejar OPTIONS
        if (request.getMethod().equals("OPTIONS")) {
            System.out.println("📋 [JwtFilter] Petición OPTIONS detectada, permitiendo...");
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        System.out.println("📋 [JwtFilter] Auth header: " + (authHeader != null ? "Presente" : "No presente"));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("❌ [JwtFilter] No hay token Bearer, continuando cadena...");
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        System.out.println("🔑 [JwtFilter] Token extraído (primeros 20 chars): " +
                jwt.substring(0, Math.min(20, jwt.length())) + "...");

        // 👇 NUEVO: Verificar si el token está en blacklist
        if (tokenBlacklistService.isBlacklisted(jwt)) {
            System.out.println("🚫 [JwtFilter] TOKEN EN BLACKLIST - Acceso denegado");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token inválido o expirado");
            return;
        }

        final String userEmail = jwtService.extractUsername(jwt);
        System.out.println("👤 [JwtFilter] Email extraído: " + userEmail);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            System.out.println("🔄 [JwtFilter] Cargando usuario de BD...");
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                System.out.println("✅ [JwtFilter] Token válido, autenticando...");
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                System.out.println("❌ [JwtFilter] Token inválido o expirado");
            }
        }

        filterChain.doFilter(request, response);
        System.out.println("🏁 [JwtFilter] Finalizado");
    }
}
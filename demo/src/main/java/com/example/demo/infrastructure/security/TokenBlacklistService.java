package com.example.demo.infrastructure.security;

import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

@Service
public class TokenBlacklistService {

    // Almacenamiento en memoria de tokens blacklisted
    private final Set<String> blacklistedTokens = new HashSet<>();

    /**
     * Agrega un token a la blacklist
     */
    public void blacklistToken(String token) {
        blacklistedTokens.add(token);
        System.out.println("🔴 Token agregado a blacklist: " + token.substring(0, Math.min(20, token.length())) + "...");
    }

    /**
     * Verifica si un token está en blacklist
     */
    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }

    /**
     * Obtener cantidad de tokens en blacklist (para debugging)
     */
    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }
}
package com.example.demo.domain.mision;

/** Lo que el sistema de misiones necesita saber del usuario, aparte de lo que hizo. La implementación real es PerfilUsuarioAdaptador. */
public interface PerfilUsuario {
    /** ¿Tiene una suscripción Premium activa ahora? */
    boolean esPremium(long userId);
}

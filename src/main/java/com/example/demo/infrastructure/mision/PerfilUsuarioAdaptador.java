package com.example.demo.infrastructure.mision;

import com.example.demo.domain.mision.PerfilUsuario;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Component;

/** Responde si un usuario es Premium usando la misma regla que el resto de la aplicación (User.isActivePremium). */
@Component
public class PerfilUsuarioAdaptador implements PerfilUsuario {

    private final UserRepository userRepository;

    public PerfilUsuarioAdaptador(UserRepository userRepository) { this.userRepository = userRepository; }

    @Override
    public boolean esPremium(long userId) {
        return userRepository.findById(userId).map(User::isActivePremium).orElse(false);
    }
}

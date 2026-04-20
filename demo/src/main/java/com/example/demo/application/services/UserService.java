package com.example.demo.application.services;

import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import com.example.demo.domain.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestionar usuarios
 * Incluye lógica de niveles, puntos y avatares
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AvatarService avatarService;
    private final LevelCalculatorService levelCalculatorService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AvatarService avatarService,
                       LevelCalculatorService levelCalculatorService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.avatarService = avatarService;
        this.levelCalculatorService = levelCalculatorService;
    }

    // ==============================================
    // MÉTODOS BÁSICOS CRUD
    // ==============================================

    /**
     * Obtiene todos los usuarios
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Obtiene un usuario por ID
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Obtiene un usuario por email
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Crea un nuevo usuario
     */
    @Transactional
    public User createUser(String name, String email, String password, UserRole role) {
        // Verificar si ya existe
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role != null ? role : UserRole.USER);

        // Asignar avatar por defecto según nivel inicial (AMATEUR)
        assignDefaultAvatar(user);

        return userRepository.save(user);
    }

    /**
     * Actualiza un usuario existente
     */
    @Transactional
    public User updateUser(Long id, String name, String dni, String phone) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setName(name);
        user.setDni(dni);
        user.setPhone(phone);

        return userRepository.save(user);
    }

    /**
     * Elimina (desactiva) un usuario
     */
    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setActive(false);
        userRepository.save(user);
    }

    // ==============================================
    // GESTIÓN DE PUNTOS Y NIVELES
    // ==============================================

    /**
     * Agrega puntos a un usuario y actualiza su nivel si corresponde
     * El nivel se calcula SIEMPRE mediante LevelCalculatorService (Sistema 2)
     */
    @Transactional
    public User addPoints(Long userId, int points) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.addPoints(points);

        // Usar SOLO LevelCalculatorService para calcular el nivel
        UserLevel oldLevel = user.getLevel();
        UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

        if (oldLevel != newLevel) {
            user.setLevel(newLevel);
            user.setLevelUpdatedAt(LocalDateTime.now());
            assignDefaultAvatar(user);
        }

        return userRepository.save(user);
    }

    /**
     * Resta puntos a un usuario (ej: por canje)
     */
    @Transactional
    public User subtractPoints(Long userId, int points) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.subtractPoints(points);
        // No bajamos de nivel automáticamente

        return userRepository.save(user);
    }

    /**
     * Actualiza el nivel de un usuario basado en sus puntos
     * El nivel se calcula SIEMPRE mediante LevelCalculatorService (Sistema 2)
     */
    @Transactional
    public User recalculateLevel(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        UserLevel oldLevel = user.getLevel();
        UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

        if (oldLevel != newLevel) {
            user.setLevel(newLevel);
            user.setLevelUpdatedAt(LocalDateTime.now());
            assignDefaultAvatar(user);
        }

        return userRepository.save(user);
    }

    /**
     * Recalcula el nivel de todos los usuarios (útil después de cambios en la lógica)
     * El nivel se calcula SIEMPRE mediante LevelCalculatorService (Sistema 2)
     */
    @Transactional
    public int recalculateAllLevels() {
        List<User> users = userRepository.findAll();
        int changed = 0;

        for (User user : users) {
            UserLevel oldLevel = user.getLevel();
            UserLevel newLevel = levelCalculatorService.calculateUserLevel(user);

            if (oldLevel != newLevel) {
                user.setLevel(newLevel);
                user.setLevelUpdatedAt(LocalDateTime.now());
                assignDefaultAvatar(user);
                changed++;
            }
        }

        userRepository.saveAll(users);
        return changed;
    }

    /**
     * Obtiene usuarios que están cerca de subir de nivel
     */
    @Transactional(readOnly = true)
    public List<User> getUsersEligibleForLevelUp() {
        return userRepository.findUsersEligibleForLevelUp();
    }

    // ==============================================
    // GESTIÓN DE AVATARES
    // ==============================================

    /**
     * Asigna el avatar por defecto correspondiente al nivel del usuario
     */
    private void assignDefaultAvatar(User user) {
        try {
            String avatarUrl = avatarService.getDefaultAvatarForLevel(user.getLevel()).getImageUrl();
            user.setAvatarUrl(avatarUrl);
        } catch (Exception e) {
            // Si no hay avatar configurado, no hacemos nada (se usará el genérico)
            System.out.println("⚠️ No se pudo asignar avatar por defecto: " + e.getMessage());
        }
    }

    /**
     * Actualiza el avatar de un usuario (avatar predefinido)
     */
    @Transactional
    public User updateAvatar(Long userId, Long avatarId) {
        return avatarService.assignAvatarToUser(userId, avatarId);
    }

    /**
     * Sube y asigna un avatar personalizado
     */
    @Transactional
    public User uploadCustomAvatar(Long userId, MultipartFile file) {
        return avatarService.assignCustomAvatar(userId, file);
    }

    /**
     * Restablece al avatar por defecto del nivel actual
     */
    @Transactional
    public User resetToDefaultAvatar(Long userId) {
        return avatarService.resetToDefaultAvatar(userId);
    }

    // ==============================================
    // ESTADÍSTICAS
    // ==============================================

    /**
     * Obtiene estadísticas de usuarios por nivel
     */
    @Transactional(readOnly = true)
    public List<Object[]> getUsersCountByLevel() {
        return userRepository.countUsersByLevel();
    }

    /**
     * Obtiene el nivel promedio de los usuarios
     */
    @Transactional(readOnly = true)
    public List<Object[]> getLevelDistribution() {
        return userRepository.getLevelDistribution();
    }

    /**
     * Obtiene los usuarios de nivel más alto (top)
     */
    @Transactional(readOnly = true)
    public List<User> getTopLevelUsers(int limit) {
        return userRepository.findTopLevelUsers(PageRequest.of(0, limit));
    }

    // ==============================================
    // SUSPENSIÓN DE USUARIOS
    // ==============================================

    /**
     * Suspende un usuario
     */
    @Transactional
    public User suspendUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.suspend(reason);
        return userRepository.save(user);
    }

    /**
     * Reactiva un usuario suspendido
     */
    @Transactional
    public User unsuspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.unsuspend();
        return userRepository.save(user);
    }

    // ==============================================
    // VERIFICACIÓN DE EMAIL
    // ==============================================

    /**
     * Verifica el email de un usuario
     */
    @Transactional
    public User verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token de verificación inválido"));

        user.setEmailVerified(true);
        user.setVerificationToken(null);

        return userRepository.save(user);
    }

    /**
     * Genera token para restablecer contraseña
     */
    @Transactional
    public User generateResetToken(String email, String token) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setResetPasswordToken(token);
        return userRepository.save(user);
    }

    /**
     * Restablece contraseña usando token
     */
    @Transactional
    public User resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new RuntimeException("Token inválido"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);

        return userRepository.save(user);
    }

    /**
     * Actualiza el nivel de un usuario (usado cuando cambia por puntos)
     */
    @Transactional
    public User updateUserLevel(Long userId, UserLevel newLevel) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        user.setLevel(newLevel);
        user.setLevelUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }
}
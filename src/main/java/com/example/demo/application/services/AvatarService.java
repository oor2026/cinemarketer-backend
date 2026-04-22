package com.example.demo.application.services;

import com.example.demo.domain.avatar.Avatar;
import com.example.demo.domain.avatar.AvatarRepository;
import com.example.demo.domain.user.User;
import com.example.demo.domain.user.UserLevel;
import com.example.demo.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar avatares
 * Proporciona métodos para:
 * - Obtener avatares disponibles según nivel del usuario
 * - Asignar avatar a usuario
 * - Gestionar avatares predefinidos (CRUD para admin)
 * - Subir avatares personalizados
 */
@Service
public class AvatarService {

    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    public AvatarService(AvatarRepository avatarRepository,
                         UserRepository userRepository,
                         CloudinaryService cloudinaryService) {
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // ==============================================
    // MÉTODOS PARA USUARIOS
    // ==============================================

    /**
     * Obtiene todos los avatares disponibles para un usuario
     * según su nivel y los avatares generales
     */
    @Transactional(readOnly = true)
    public List<Avatar> getAvatarsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return avatarRepository.findAvailablesForUserLevel(user.getLevel());
    }

    /**
     * Obtiene el avatar por defecto para un nivel específico
     */
    @Transactional(readOnly = true)
    public Avatar getDefaultAvatarForLevel(UserLevel level) {
        String category = getCategoryForLevel(level);
        return avatarRepository.findDefaultByCategory(category)
                .orElseGet(() -> avatarRepository.findByIsDefaultTrueAndActiveTrue()
                        .orElseThrow(() -> new RuntimeException("No hay avatar por defecto configurado")));
    }

    /**
     * Asigna un avatar predefinido a un usuario
     */
    @Transactional
    public User assignAvatarToUser(Long userId, Long avatarId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Avatar avatar = avatarRepository.findById(avatarId)
                .orElseThrow(() -> new RuntimeException("Avatar no encontrado"));

        // Verificar que el avatar esté disponible para el nivel del usuario
        if (!avatar.isAvailableForLevel(user.getLevel())) {
            throw new RuntimeException("Este avatar no está disponible para tu nivel actual");
        }

        user.updateAvatar(avatar.getImageUrl());
        return userRepository.save(user);
    }

    /**
     * Asigna un avatar personalizado (subido por el usuario)
     */
    @Transactional
    public User assignCustomAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        try {
            // Subir imagen a Cloudinary
            String imageUrl = cloudinaryService.uploadImage(file, "avatars/custom");
            user.updateAvatar(imageUrl);
            return userRepository.save(user);

        } catch (IOException e) {
            throw new RuntimeException("Error al subir la imagen a Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Elimina el avatar personalizado y vuelve al avatar por defecto de su nivel
     */
    @Transactional
    public User resetToDefaultAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Avatar defaultAvatar = getDefaultAvatarForLevel(user.getLevel());
        user.updateAvatar(defaultAvatar.getImageUrl());
        return userRepository.save(user);
    }

    // ==============================================
    // MÉTODOS PARA ADMIN (GESTIÓN DE AVATARES PREDEFINIDOS)
    // ==============================================

    /**
     * Obtiene todos los avatares (para admin)
     */
    @Transactional(readOnly = true)
    public List<Avatar> getAllAvatars() {
        return avatarRepository.findAll();
    }

    /**
     * Obtiene avatares activos ordenados
     */
    @Transactional(readOnly = true)
    public List<Avatar> getActiveAvatars() {
        return avatarRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    /**
     * Obtiene avatares por categoría
     */
    @Transactional(readOnly = true)
    public List<Avatar> getAvatarsByCategory(String category) {
        return avatarRepository.findByCategoryAndActiveTrueOrderBySortOrderAsc(category);
    }

    /**
     * Crea un nuevo avatar predefinido
     */
    @Transactional
    public Avatar createAvatar(Avatar avatar, MultipartFile imageFile) {
        try {
            // Subir imagen a Cloudinary
            String imageUrl = cloudinaryService.uploadImage(imageFile, "avatars/predefined");
            avatar.setImageUrl(imageUrl);

            // Generar miniatura (podría ser la misma imagen redimensionada)
            String thumbnailUrl = cloudinaryService.uploadImage(imageFile, "avatars/thumbnails");
            avatar.setThumbnailUrl(thumbnailUrl);

            return avatarRepository.save(avatar);

        } catch (IOException e) {
            throw new RuntimeException("Error al subir la imagen del avatar: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza un avatar existente
     */
    @Transactional
    public Avatar updateAvatar(Long id, Avatar avatarDetails, MultipartFile imageFile) {
        Avatar avatar = avatarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Avatar no encontrado"));

        avatar.setName(avatarDetails.getName());
        avatar.setCategory(avatarDetails.getCategory());
        avatar.setRequiredLevel(avatarDetails.getRequiredLevel());
        avatar.setSortOrder(avatarDetails.getSortOrder());
        avatar.setActive(avatarDetails.isActive());

        // Si se subió una nueva imagen, actualizarla
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String imageUrl = cloudinaryService.uploadImage(imageFile, "avatars/predefined");
                avatar.setImageUrl(imageUrl);

                String thumbnailUrl = cloudinaryService.uploadImage(imageFile, "avatars/thumbnails");
                avatar.setThumbnailUrl(thumbnailUrl);
            } catch (IOException e) {
                throw new RuntimeException("Error al actualizar la imagen del avatar: " + e.getMessage(), e);
            }
        }

        return avatarRepository.save(avatar);
    }

    /**
     * Elimina (desactiva) un avatar
     */
    @Transactional
    public Avatar deactivateAvatar(Long id) {
        Avatar avatar = avatarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Avatar no encontrado"));

        if (avatar.isDefault()) {
            throw new RuntimeException("No se puede desactivar un avatar por defecto");
        }

        avatar.setActive(false);
        return avatarRepository.save(avatar);
    }

    // ==============================================
    // MÉTODOS AUXILIARES
    // ==============================================

    /**
     * Obtiene la categoría correspondiente a un nivel
     */
    private String getCategoryForLevel(UserLevel level) {
        switch (level) {
            case AMATEUR: return "amateur";
            case COLABORADOR: return "colaborador";
            case CRITICO: return "critico";
            case JURADO_EXPERTO: return "jurado";
            default: return "general";
        }
    }

    /**
     * Obtiene estadísticas de uso de avatares
     */
    @Transactional(readOnly = true)
    public AvatarStats getAvatarStats() {
        AvatarStats stats = new AvatarStats();

        long total    = avatarRepository.count();
        long activos  = avatarRepository.countByActiveTrue();

        stats.setTotalAvatars(total);
        stats.setActiveAvatars(activos);
        stats.setInactiveAvatars(total - activos);
        stats.setUsedByUsers(avatarRepository.countUsersUsingPredefinedAvatar());
        stats.setAvatarsByCategory(avatarRepository.countByCategory());
        stats.setAvatarsByLevel(avatarRepository.countByRequiredLevel());

        return stats;
    }

    // ==============================================
    // INNER CLASS PARA ESTADÍSTICAS
    // ==============================================
    @lombok.Data
    public static class AvatarStats {
        private long totalAvatars;
        private long activeAvatars;
        private long inactiveAvatars;
        private long usedByUsers;
        private List<Object[]> avatarsByCategory;
        private List<Object[]> avatarsByLevel;

        public long getTotalAvatars()  { return totalAvatars; }
        public void setTotalAvatars(long v)  { this.totalAvatars = v; }

        public long getActiveAvatars() { return activeAvatars; }
        public void setActiveAvatars(long v) { this.activeAvatars = v; }

        public long getInactiveAvatars() { return inactiveAvatars; }
        public void setInactiveAvatars(long v) { this.inactiveAvatars = v; }

        public long getUsedByUsers()   { return usedByUsers; }
        public void setUsedByUsers(long v)   { this.usedByUsers = v; }

        public List<Object[]> getAvatarsByCategory() { return avatarsByCategory; }
        public void setAvatarsByCategory(List<Object[]> v) { this.avatarsByCategory = v; }

        public List<Object[]> getAvatarsByLevel() { return avatarsByLevel; }
        public void setAvatarsByLevel(List<Object[]> v) { this.avatarsByLevel = v; }
    }

    /**
     * Obtiene un avatar por su ID
     * @param id ID del avatar
     * @return el avatar encontrado
     * @throws RuntimeException si no existe
     */
    @Transactional(readOnly = true)
    public Avatar getAvatarById(Long id) {
        return avatarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Avatar no encontrado con ID: " + id));
    }

    /**
     * Activa un avatar
     * @param id ID del avatar a activar
     * @return el avatar activado
     */
    @Transactional
    public Avatar activateAvatar(Long id) {
        Avatar avatar = avatarRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Avatar no encontrado con ID: " + id));

        avatar.setActive(true);
        return avatarRepository.save(avatar);
    }

    @Transactional
    public void deleteAvatar(Long id) {
        Avatar avatar = getAvatarById(id);

        // Resetear avatarUrl de todos los usuarios que tengan este avatar asignado
        userRepository.resetAvatarUrlForUsers(avatar.getImageUrl());

        avatarRepository.delete(avatar);
    }

    public Optional<String> getAvatarNameByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return Optional.empty();
        return avatarRepository.findByImageUrl(imageUrl)
                .map(Avatar::getName);
    }
}
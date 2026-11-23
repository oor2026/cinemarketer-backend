package com.example.demo.application.services;

import com.example.demo.application.services.JuegoAdminService.JuegoException;
import com.example.demo.domain.juego.JuegoCampania;
import com.example.demo.domain.juego.JuegoCampaniaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Imágenes de una campaña, subidas a Cloudinary con el mismo servicio que
 * usan los premios. Cada imagen se guarda en la campaña apenas se sube; si
 * reemplaza a otra, la anterior se borra de Cloudinary.
 *
 * Tipos:
 *   banner-desktop  → fondoUrl          (1920 × 480, escritorio)
 *   banner-mobile   → bannerMobileUrl   (1080 × 1080, celular)
 *   fondo-lateral   → fondoLateralUrl   (solo escritorio, se ve por los costados)
 *   premio          → premioImagenUrl
 */
@Service
public class JuegoImagenService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final Set<String> FORMATOS = Set.of("image/jpeg", "image/png", "image/webp");
    static final String CARPETA = "cinemarketer/juegos";

    private final JuegoAdminService adminService;
    private final JuegoCampaniaRepository campaniaRepository;
    private final CloudinaryService cloudinaryService;

    public JuegoImagenService(JuegoAdminService adminService,
                              JuegoCampaniaRepository campaniaRepository,
                              CloudinaryService cloudinaryService) {
        this.adminService = adminService;
        this.campaniaRepository = campaniaRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional
    public JuegoCampania subir(Long campaniaId, String tipo, MultipartFile archivo) {
        JuegoCampania c = campaniaEditable(campaniaId);
        validarTipo(tipo);
        if (archivo == null || archivo.isEmpty()) throw new JuegoException(400, "No llegó ninguna imagen");
        if (archivo.getSize() > MAX_BYTES) throw new JuegoException(400, "La imagen pesa más de 5 MB");
        String formato = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase();
        if (!FORMATOS.contains(formato)) throw new JuegoException(400, "La imagen tiene que ser JPG, PNG o WEBP");

        String url;
        try {
            url = cloudinaryService.uploadImage(archivo, CARPETA);
        } catch (Exception e) {
            throw new JuegoException(502, "No se pudo subir la imagen. Probá de nuevo.");
        }
        String anterior = leer(c, tipo);
        escribir(c, tipo, url);
        campaniaRepository.save(c);
        borrarDeCloudinary(anterior);
        return c;
    }

    @Transactional
    public JuegoCampania quitar(Long campaniaId, String tipo) {
        JuegoCampania c = campaniaEditable(campaniaId);
        validarTipo(tipo);
        String anterior = leer(c, tipo);
        escribir(c, tipo, null);
        campaniaRepository.save(c);
        borrarDeCloudinary(anterior);
        return c;
    }

    private JuegoCampania campaniaEditable(Long id) {
        JuegoCampania c = adminService.buscar(id);
        if (JuegoCampania.ARCHIVADA.equals(c.getEstado())) throw new JuegoException(409, "La campaña está archivada");
        return c;
    }

    private static void validarTipo(String tipo) {
        if (!Set.of("banner-desktop", "banner-mobile", "fondo-lateral", "premio").contains(tipo)) {
            throw new JuegoException(400, "Tipo de imagen desconocido");
        }
    }

    private static String leer(JuegoCampania c, String tipo) {
        return switch (tipo) {
            case "banner-desktop" -> c.getFondoUrl();
            case "banner-mobile" -> c.getBannerMobileUrl();
            case "fondo-lateral" -> c.getFondoLateralUrl();
            default -> c.getPremioImagenUrl();
        };
    }

    private static void escribir(JuegoCampania c, String tipo, String url) {
        switch (tipo) {
            case "banner-desktop" -> c.setFondoUrl(url);
            case "banner-mobile" -> c.setBannerMobileUrl(url);
            case "fondo-lateral" -> c.setFondoLateralUrl(url);
            default -> c.setPremioImagenUrl(url);
        }
    }

    // Solo las que están en Cloudinary (una URL vieja de TMDb, por ejemplo, no se toca).
    private void borrarDeCloudinary(String url) {
        if (url == null || !url.contains("res.cloudinary.com")) return;
        try { cloudinaryService.deleteImage(url); } catch (Exception ignorado) { /* queda huérfana, no traba nada */ }
    }
}

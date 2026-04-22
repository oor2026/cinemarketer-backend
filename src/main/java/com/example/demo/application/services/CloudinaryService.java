package com.example.demo.application.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret
        ));
    }

    /**
     * Sube una imagen a Cloudinary y devuelve la URL pública
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "folder", folder,
                        "resource_type", "image"
                )
        );
        return (String) result.get("secure_url");
    }

    /**
     * Elimina una imagen de Cloudinary por su public_id
     */
    public void deleteImage(String imageUrl) throws IOException {
        // Extraer public_id de la URL
        if (imageUrl == null || imageUrl.isEmpty()) return;
        String publicId = extractPublicId(imageUrl);
        cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
    }

    private String extractPublicId(String imageUrl) {
        // URL formato: https://res.cloudinary.com/{cloud}/image/upload/v123/{folder}/{id}.jpg
        String[] parts = imageUrl.split("/");
        String fileWithExt = parts[parts.length - 1];
        String folder = parts[parts.length - 2];
        String fileName = fileWithExt.substring(0, fileWithExt.lastIndexOf('.'));
        return folder + "/" + fileName;
    }
}

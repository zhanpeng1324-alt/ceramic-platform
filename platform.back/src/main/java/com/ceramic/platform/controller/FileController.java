package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    /** 上传目录固定路径；未配置时回退到工作目录下的 uploads（与 WebMvcConfig 的读取目录保持同源） */
    @Value("${app.upload.dir:}")
    private String uploadDir;

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private Path uploadDirPath() {
        String base = (uploadDir == null || uploadDir.isBlank())
                ? Paths.get(System.getProperty("user.dir"), "uploads").toString()
                : uploadDir;
        return Paths.get(base).toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_IMAGE_SIZE || !IMAGE_TYPES.contains(file.getContentType())) {
            return Result.error(400, "仅支持 JPG、PNG、GIF、WebP 图片，且不能超过 5MB");
        }

        try {
            BufferedImage image;
            try (InputStream input = file.getInputStream()) {
                image = ImageIO.read(input);
            }
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 ||
                    (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                return Result.error(400, "Invalid image file or image dimensions are too large");
            }
            if (!Files.exists(uploadDirPath())) {
                Files.createDirectories(uploadDirPath());
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String filename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadDirPath().resolve(filename).normalize();
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String url = "/uploads/" + filename;
            return Result.success(url);
        } catch (IOException e) {
            return Result.error(500, "文件上传失败：" + e.getMessage());
        }
    }
}

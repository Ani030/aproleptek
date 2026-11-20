package hu.aproleptek.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalStorageService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", ".png", "image/jpeg", ".jpg", "image/webp", ".webp", "application/pdf", ".pdf");
    private final Path root;

    public LocalStorageService(@Value("${app.storage.root}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public StoredFile save(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("A feltöltött fájl üres.");
        if (file.getSize() > 20L * 1024 * 1024) throw new IllegalArgumentException("A fájl legfeljebb 20 MB lehet.");
        byte[] bytes = file.getBytes();
        String type = detectType(bytes);
        if (type == null || !type.equalsIgnoreCase(file.getContentType())) {
            throw new IllegalArgumentException("A fájl tartalma nem egyezik a kiválasztott fájltípussal.");
        }
        String extension = EXTENSIONS.get(type);
        if (extension == null) throw new IllegalArgumentException("Csak PNG, JPEG, WebP és PDF tölthető fel.");
        String key = UUID.randomUUID() + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Érvénytelen fájlnév.");
        try {
            Files.write(target, bytes);
        } catch (IOException ex) {
            try { Files.deleteIfExists(target); }
            catch (IOException cleanupError) { ex.addSuppressed(cleanupError); }
            throw ex;
        }
        return new StoredFile(key, type, file.getSize());
    }

    public void delete(String key) throws IOException {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Érvénytelen fájlnév.");
        Files.deleteIfExists(target);
    }

    private String detectType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') return "application/pdf";
        return null;
    }

    public Resource load(String key) throws IOException {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) throw new IOException("A fájl nem található.");
        return new UrlResource(target.toUri());
    }

    public record StoredFile(String key, String mediaType, long size) { }
}


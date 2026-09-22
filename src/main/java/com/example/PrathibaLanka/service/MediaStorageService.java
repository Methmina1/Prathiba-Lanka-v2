package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.enums.MediaType;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.PayloadTooLargeException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes uploaded images and short videos to disk.
 *
 * <p>The stored file name is always generated ({@code <uuid>.<ext>}) and the extension comes from
 * the table below, never from the client - so a request cannot choose where a file lands or what it
 * is served as. Only the listed content types are accepted.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    /** content type -> file extension. Anything not listed here is refused. */
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "image/avif", ".avif");

    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", ".mp4",
            "video/webm", ".webm",
            "video/quicktime", ".mov");

    /**
     * First bytes of each accepted format. The extension we store under decides how the file is
     * served later, so a file has to actually look like what it claims to be. Every entry must
     * match, which lets WEBP check both its RIFF container and its WEBP marker.
     */
    private record Magic(int offset, byte[] bytes) {
    }

    private static final Map<String, List<Magic>> SIGNATURES = Map.of(
            "image/jpeg", List.of(new Magic(0, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})),
            "image/png", List.of(new Magic(0, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})),
            "image/gif", List.of(new Magic(0, new byte[]{'G', 'I', 'F', '8'})),
            "image/webp", List.of(new Magic(0, new byte[]{'R', 'I', 'F', 'F'}), new Magic(8, new byte[]{'W', 'E', 'B', 'P'})),
            "image/avif", List.of(new Magic(4, new byte[]{'f', 't', 'y', 'p'})),
            "video/mp4", List.of(new Magic(4, new byte[]{'f', 't', 'y', 'p'})),
            "video/quicktime", List.of(new Magic(4, new byte[]{'f', 't', 'y', 'p'})),
            "video/webm", List.of(new Magic(0, new byte[]{(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3})));

    private final Path root;
    private final String urlPrefix;
    private final long maxImageBytes;
    private final long maxVideoBytes;

    public MediaStorageService(@Value("${app.media.dir:uploads}") String directory,
                               @Value("${app.media.url-prefix:/media}") String urlPrefix,
                               @Value("${app.media.max-image-bytes:10485760}") long maxImageBytes,
                               @Value("${app.media.max-video-bytes:62914560}") long maxVideoBytes) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
    }

    @PostConstruct
    void prepare() {
        try {
            Files.createDirectories(root);
            log.info("Media directory: {} (public path {}/)", root, urlPrefix);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create the media directory " + root, ex);
        }
    }

    /** Everything the caller needs to persist an {@code MediaAsset} row. */
    public record StoredFile(String storedName, String contentType, MediaType mediaType,
                             long sizeBytes, String url) {
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        MediaType mediaType = typeOf(contentType);
        String extension = extensionOf(contentType, mediaType);

        long limit = mediaType == MediaType.VIDEO ? maxVideoBytes : maxImageBytes;
        if (file.getSize() > limit) {
            throw new PayloadTooLargeException("%s is %s; the limit for %s is %s."
                    .formatted(displayName(file), humanSize(file.getSize()),
                            mediaType == MediaType.VIDEO ? "videos" : "images", humanSize(limit)));
        }

        String storedName = UUID.randomUUID() + extension;
        Path target = resolve(storedName);

        verifyContents(contentType, file);

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // A copy that fails part way through leaves a file behind that no row points at, and a
            // 0-byte one if it failed immediately - which is how stray files appear in the media
            // directory. Remove it: the upload is being refused anyway.
            delete(storedName);
            throw new IllegalStateException("Could not store the uploaded file", ex);
        }

        return new StoredFile(storedName, contentType, mediaType, file.getSize(), urlPrefix + "/" + storedName);
    }

    /** Removes a file. A file that is already gone is not an error. */
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ex) {
            log.warn("Could not delete media file '{}': {}", storedName, ex.getMessage());
        }
    }

    public Path getRoot() {
        return root;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    /** Rejects anything that is not a generated name, so a stored value can never escape the root. */
    private Path resolve(String storedName) {
        if (storedName == null || !storedName.matches("[A-Za-z0-9-]+\\.[A-Za-z0-9]{2,5}")) {
            throw new BadRequestException("Invalid media file name.");
        }
        return root.resolve(storedName).normalize();
    }

    private MediaType typeOf(String contentType) {
        if (IMAGE_TYPES.containsKey(contentType)) return MediaType.IMAGE;
        if (VIDEO_TYPES.containsKey(contentType)) return MediaType.VIDEO;
        throw new BadRequestException("Unsupported file type '" + (contentType.isBlank() ? "unknown" : contentType)
                + "'. Images: " + String.join(", ", IMAGE_TYPES.keySet())
                + ". Videos: " + String.join(", ", VIDEO_TYPES.keySet()) + ".");
    }

    private String extensionOf(String contentType, MediaType mediaType) {
        return (mediaType == MediaType.VIDEO ? VIDEO_TYPES : IMAGE_TYPES).get(contentType);
    }

    /** Refuses a file whose contents do not start the way its declared type must. */
    private void verifyContents(String contentType, MultipartFile file) {
        List<Magic> magics = SIGNATURES.get(contentType);
        if (magics == null || magics.isEmpty()) {
            return;
        }

        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(32);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded file", ex);
        }

        for (Magic magic : magics) {
            if (!matches(head, magic)) {
                throw new BadRequestException("That file does not look like " + contentType
                        + ". Upload the file itself rather than renaming it.");
            }
        }
    }

    private boolean matches(byte[] head, Magic magic) {
        if (head.length < magic.offset() + magic.bytes().length) {
            return false;
        }
        for (int i = 0; i < magic.bytes().length; i++) {
            if (head[magic.offset() + i] != magic.bytes()[i]) {
                return false;
            }
        }
        return true;
    }

    private String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "The file" : name;
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024 * 1024) {
            return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
        }
        return Math.max(1, bytes / 1024) + " KB";
    }

    /** Accepted types, used by the API to tell the console what it may upload. */
    public Map<String, Object> describeLimits() {
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("images", IMAGE_TYPES.keySet().stream().sorted().collect(Collectors.toList()));
        limits.put("videos", VIDEO_TYPES.keySet().stream().sorted().collect(Collectors.toList()));
        limits.put("maxImageBytes", maxImageBytes);
        limits.put("maxVideoBytes", maxVideoBytes);
        limits.put("urlPrefix", urlPrefix);
        return limits;
    }
}

package co.edu.ufps.notsonaive.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the downloadable ZIP with the complete NetBeans/Maven source project.
 *
 * <p>The project root is configurable ({@code app.project-root}). When running
 * from NetBeans it defaults to the working directory; in the Docker image the
 * sources are copied to {@code /app/source}. Build output and IDE-private
 * folders are excluded. The archive is built once and cached.</p>
 */
@Service
public class ProjectArchiveService {

    public static final String ARCHIVE_NAME = "not-so-naive-project.zip";
    private static final String ROOT_FOLDER = "not-so-naive/";
    private static final Set<String> EXCLUDED_DIRS = Set.of("target", ".git", ".idea", ".vscode", "private", "node_modules");

    private final Path root;
    private volatile byte[] cached;

    public ProjectArchiveService(@Value("${app.project-root:}") String projectRoot) {
        this.root = (projectRoot == null || projectRoot.isBlank())
                ? Path.of(System.getProperty("user.dir")) : Path.of(projectRoot);
    }

    /** @return true when the project sources (pom.xml) are available */
    public boolean isAvailable() {
        return Files.isRegularFile(root.resolve("pom.xml"));
    }

    public byte[] archive() {
        byte[] local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = build();
            }
            return cached;
        }
    }

    private byte[] build() {
        if (!isAvailable()) {
            throw new IllegalStateException("Project sources are not available on this server.");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes);
             Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> !isExcluded(root.relativize(p)))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String name = ROOT_FOLDER + root.relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(file, zip);
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the project archive", e);
        }
    }

    private static boolean isExcluded(Path relative) {
        for (Path part : relative) {
            if (EXCLUDED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}

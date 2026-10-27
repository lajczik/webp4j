package xyz.lychee.webp4j.natives;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Loads the native platform binary.
 * <p>
 * Lookup order:
 * <ol>
 *   <li>the file named by the system property {@code webp4j.native.path} (for custom builds, for
 *       example musl/Alpine or unsupported architectures);</li>
 *   <li>the binary bundled in the jar, extracted once into a per-user cache directory named after
 *       the SHA-256 of the binary ({@code webp4j.tmpdir}, default {@code java.io.tmpdir}). The
 *       directory is reused by every JVM, never accumulates stale copies, and the content is
 *       verified before it is loaded;</li>
 *   <li>{@code System.loadLibrary("webp4j")} from {@code java.library.path}.</li>
 * </ol>
 *
 * @author MrNanko
 * @author lajczik
 */
public class NativeLibraryLoader {

    /** System property: absolute path of a native library to load instead of the bundled one. */
    public static final String PROPERTY_NATIVE_PATH = "webp4j.native.path";
    /** System property: directory under which the bundled binary is extracted. */
    public static final String PROPERTY_TMP_DIR = "webp4j.tmpdir";

    private static volatile File loadedLibraryFile;
    private static boolean loaded;

    private NativeLibraryLoader() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static File getLoadedLibraryFile() {
        return loadedLibraryFile;
    }

    public static synchronized void loadLibrary() {
        if (loaded) {
            return;
        }

        String override = System.getProperty(PROPERTY_NATIVE_PATH);
        if (override != null && !override.isEmpty()) {
            File file = new File(override).getAbsoluteFile();
            if (!file.isFile()) {
                throw new UnsupportedOperationException(PROPERTY_NATIVE_PATH + " does not point to a file: " + file);
            }
            System.load(file.getPath());
            loadedLibraryFile = file;
            loaded = true;
            return;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String libraryFileName = getLibraryFileName(os, arch);
        String resourcePath = "/native/" + libraryFileName;

        InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                in = cl.getResourceAsStream(resourcePath.substring(1));
            }
        }

        if (in == null) {
            try {
                System.loadLibrary("webp4j");
                loaded = true;
                return;
            } catch (UnsatisfiedLinkError notOnLibraryPath) {
                throw new UnsupportedOperationException(
                        String.format("Native library not found: %s%n" +
                                        "OS: %s, Architecture: %s%n" +
                                        "Expected resource: %s%n" +
                                        "Supported platforms: Linux (x64/aarch64, glibc), macOS (x64/arm64), Windows (x64/aarch64).%n" +
                                        "For other platforms build the library and point -D%s to it.",
                                libraryFileName, os, arch, resourcePath, PROPERTY_NATIVE_PATH),
                        notOnLibraryPath);
            }
        }

        try (InputStream input = in) {
            File file = extract(input, libraryFileName);
            System.load(file.getAbsolutePath());
            loadedLibraryFile = file;
            loaded = true;
        } catch (IOException e) {
            throw new UnsupportedOperationException("Could not load native WebP library", e);
        }
    }

    /**
     * Extracts the binary to {@code <tmp>/webp4j-<sha256>/<name>} unless an identical copy is already there.
     */
    private static File extract(InputStream input, String libraryFileName) throws IOException {
        byte[] bytes = readAll(input);
        String hash = sha256Hex(bytes);

        String configured = System.getProperty(PROPERTY_TMP_DIR);
        Path base = Paths.get(configured != null && !configured.isEmpty() ? configured : System.getProperty("java.io.tmpdir"));
        Path dir = base.resolve("webp4j-" + hash.substring(0, 24));
        Path target = dir.resolve(libraryFileName);

        Files.createDirectories(dir);
        secureDirectory(dir);

        if (Files.isRegularFile(target) && sha256Hex(Files.readAllBytes(target)).equals(hash)) {
            return target.toFile();
        }

        // Write next to the target, then publish atomically so a concurrent JVM never sees a partial file.
        Path part = dir.resolve(libraryFileName + "." + UUID.randomUUID() + ".part");
        try {
            Files.write(part, bytes);
            try {
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Target is locked by another JVM that already loaded it: that copy is verified below.
                if (!Files.isRegularFile(target)) {
                    throw e;
                }
            }
        } finally {
            Files.deleteIfExists(part);
        }

        if (!sha256Hex(Files.readAllBytes(target)).equals(hash)) {
            throw new IOException("Extracted native library failed verification: " + target);
        }
        return target.toFile();
    }

    /**
     * On POSIX file systems the cache directory must belong to the current user and must not be
     * writable by anybody else; otherwise another local user could plant a library in it.
     */
    private static void secureDirectory(Path dir) throws IOException {
        if (!dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        String me = System.getProperty("user.name");
        String owner = Files.getOwner(dir).getName();
        if (me != null && !me.equals(owner)) {
            throw new IOException("Refusing to use native library cache " + dir + " owned by " + owner);
        }
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(dir);
        if (perms.contains(PosixFilePermission.GROUP_WRITE) || perms.contains(PosixFilePermission.OTHERS_WRITE)) {
            perms.remove(PosixFilePermission.GROUP_WRITE);
            perms.remove(PosixFilePermission.OTHERS_WRITE);
            Files.setPosixFilePermissions(dir, perms);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 20);
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static String getLibraryFileName(String os, String arch) {
        String platform;
        String architecture;
        String prefix;
        String extension;

        if (os.contains("win")) {
            platform = "windows";
            architecture = normalizeArchitecture(arch, false);
            prefix = "";
            extension = "dll";
        } else if (os.contains("nux") || os.contains("linux")) {
            if (isMusl()) {
                throw new UnsupportedOperationException(
                        "musl libc (Alpine Linux) is not covered by the bundled binaries, which are built for glibc. " +
                                "Build the library for musl and point -D" + PROPERTY_NATIVE_PATH + " to it.");
            }
            platform = "linux";
            architecture = normalizeArchitecture(arch, false);
            prefix = "lib";
            extension = "so";
        } else if (os.contains("mac") || os.contains("darwin")) {
            platform = "mac";
            architecture = normalizeArchitecture(arch, true);
            prefix = "lib";
            extension = "dylib";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }

        return String.format("%swebp4j-%s-%s.%s", prefix, platform, architecture, extension);
    }

    private static String normalizeArchitecture(String arch, boolean isMac) {
        switch (arch) {
            case "x86_64":
            case "amd64":
            case "x64":
                return "x64";
            case "aarch64":
            case "arm64":
                return isMac ? "arm64" : "aarch64";
            default:
                throw new UnsupportedOperationException("Unsupported architecture: " + arch
                        + " (bundled: x86_64, aarch64). Build the library and point -D" + PROPERTY_NATIVE_PATH + " to it.");
        }
    }

    /** Detects musl-based Linux (for example Alpine) by the presence of its dynamic loader. */
    private static boolean isMusl() {
        File lib = new File("/lib");
        String[] names = lib.list();
        if (names != null) {
            for (String name : names) {
                if (name.startsWith("ld-musl-")) {
                    return true;
                }
            }
        }
        return false;
    }
}

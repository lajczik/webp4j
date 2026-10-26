package xyz.lychee.webp4j.natives;

import xyz.lychee.webp4j.exception.NativeLibraryNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class NativeLibraryLoader {

    private static volatile File loadedLibraryFile;

    private NativeLibraryLoader() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static File getLoadedLibraryFile() {
        return loadedLibraryFile;
    }

    public static synchronized void loadLibrary() {
        if (loadedLibraryFile != null && loadedLibraryFile.exists()) {
            return;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libraryFileName = getLibraryFileName(os, arch);
        String resourcePath = "/native/" + libraryFileName;

        InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                in = cl.getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath);
            }
        }

        if (in == null) {
            throw new NativeLibraryNotFoundException(
                    String.format("Native library not found: %s%n" +
                                    "OS: %s, Architecture: %s%n" +
                                    "Expected path: %s%n" +
                                    "Supported platforms: Linux (x64/aarch64), macOS (x64/arm64), Windows (x64/aarch64)",
                            libraryFileName, os, arch, resourcePath)
            );
        }

        try {
            File tempLibraryFile = Files.createTempFile("webp4j-", "-" + libraryFileName).toFile();

            final File fileToDelete = tempLibraryFile;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (fileToDelete.exists()) {
                        Files.delete(fileToDelete.toPath());
                    }
                } catch (Exception ignored) {
                }
            }));

            try (InputStream input = in;
                 FileOutputStream out = new FileOutputStream(tempLibraryFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.load(tempLibraryFile.getAbsolutePath());
            loadedLibraryFile = tempLibraryFile;

        } catch (IOException e) {
            throw new NativeLibraryNotFoundException("Could not load native WebP library", e);
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
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return isMac ? "arm64" : "aarch64";
        } else if (arch.contains("64")) {
            return "x64";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        }
    }
}

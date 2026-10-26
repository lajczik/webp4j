package xyz.lychee.webp4j;


/**
 * Exception thrown when the native WebP library cannot be located or loaded.
 *
 * @author MrNanko
 * @author lajczik
 */
public class NativeLibraryNotFoundException extends RuntimeException {

    public NativeLibraryNotFoundException(String message) {
        super(message);
    }

    public NativeLibraryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

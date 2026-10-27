package xyz.lychee.webp4j;

/**
 * Single source of the library version: the {@code Implementation-Version} written into the jar
 * manifest from the Gradle project version.
 *
 * @author lajczik
 */
public final class WebPVersion {

    private static final String VERSION = resolve();

    private WebPVersion() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * @return the library version, or {@code "dev"} when running from classes outside a jar
     */
    public static String get() {
        return VERSION;
    }

    private static String resolve() {
        Package pkg = WebPVersion.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version != null ? version : "dev";
    }
}

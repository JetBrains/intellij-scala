package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.Manifest;

public class ScalaTestVersionUtils {

    private ScalaTestVersionUtils() {
    }

    public static boolean isScalaTest2or3() {
        try {
            ScalaTestRunner.class.getClassLoader().loadClass("org.scalatest.events.Location");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isOldScalaTestVersion() {
        try {
            String version = detectVersionFromClasspath();
            return isOldScalaTestVersion(version);
        } catch (IOException | ClassNotFoundException | URISyntaxException e) {
            return true;
        }
    }

    /**
     * Same code as org.jetbrains.plugins.scala.util.JarManifestUtils. We cannot use that here because this module's
     * code is injected in each test run and we're keeping it very minimal, with no Scala standard library dependency.
     */
    @Nullable
    private static String readManifestAttribute(Path jar, String attributeName) throws IOException {
        try (FileSystem fileSystem = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
            final Path manifestPath = fileSystem.getPath("META-INF", "MANIFEST.MF");
            try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(manifestPath))) {
                final Manifest manifest = new Manifest(is);
                return manifest.getMainAttributes().getValue(attributeName);
            }
        }
    }

    private static String detectVersionFromClasspath() throws ClassNotFoundException, IOException, URISyntaxException {
        String scalatestJarPath = detectScalatestJarFromInClasspath();
        final String url = URLDecoder.decode(scalatestJarPath, StandardCharsets.UTF_8.name());
        final Path jar = Paths.get(new URI(url));
        return readManifestAttribute(jar, "Bundle-Version");
    }

    @NotNull
    private static String detectScalatestJarFromInClasspath() throws ClassNotFoundException {
        Class<?> suiteClass = Class.forName("org.scalatest.Suite");
        URL location = suiteClass.getResource('/' + suiteClass.getName().replace('.', '/') + ".class");
        String path = location.getPath();
        return path.substring(5, path.indexOf("!"));
    }

    private static boolean isOldScalaTestVersion(String versionStr) {
        Version version = parseVersion(versionStr);
        return version == null || version.major == 1 && version.minor < 8;
    }

    private static Version parseVersion(String version) {
        if (version == null || version.isEmpty()) return null;
        String[] nums = version.split("\\.");
        if (nums.length < 2) return null;

        try {
            int major = Integer.parseInt(nums[0]);
            int minor = Integer.parseInt(nums[1]);
            return new Version(major, minor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class Version {
        public final int major;
        public final int minor;

        public Version(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }
    }
}

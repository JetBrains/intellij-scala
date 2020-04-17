package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.jar.JarFile;

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
        } catch (IOException | ClassNotFoundException e) {
            return true;
        }
    }

    private static String detectVersionFromClasspath() throws ClassNotFoundException, IOException {
        String scalatestJarPath = detectScalatestJarFromInClasspath();
        try (JarFile jar = new JarFile(URLDecoder.decode(scalatestJarPath, "UTF-8"))) {
            return jar.getManifest().getMainAttributes().getValue("Bundle-Version");
        }
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

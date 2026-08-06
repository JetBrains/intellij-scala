package org.jetbrains.plugins.scala.nailgun;

import com.facebook.nailgun.NGServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class Utils {

    private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

    public static Class<?> loadAndSetupServerMainNailClass(ClassLoader classLoader, Path scalaCompileServerSystemPath, Path jpsBuildSystemDir)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz = classLoader.loadClass(SERVER_CLASS_NAME);
        setupSystemDirectories(clazz, scalaCompileServerSystemPath, jpsBuildSystemDir);
        return clazz;
    }

    private static void setupSystemDirectories(Class<?> serverMainNailClass, Path scalaCompileServerSystemDir, Path jpsBuildSystemDir)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setupMethod = serverMainNailClass.getMethod("setupSystemDirectories", Path.class, Path.class);
        setupMethod.setAccessible(true);
        setupMethod.invoke(null, scalaCompileServerSystemDir, jpsBuildSystemDir);
    }

    public static void setupServerShutdownTimer(Class<?> serverMainNailClass, NGServer ngServer)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setupMethod = serverMainNailClass.getMethod("setupServerShutdownTimer", NGServer.class);
        setupMethod.setAccessible(true);
        setupMethod.invoke(null, ngServer);
    }

    private Utils() {

    }
}

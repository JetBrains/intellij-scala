package org.jetbrains.plugins.scala.nailgun;

import com.facebook.nailgun.NGServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class Utils {

    private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

    public static Class<?> loadAndSetupServerMainNailClass(ClassLoader classLoader, Path scalaCompileServerSystemPath)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz = classLoader.loadClass(SERVER_CLASS_NAME);
        setupScalaCompileServerSystemDir(clazz, scalaCompileServerSystemPath);
        return clazz;
    }

    private static void setupScalaCompileServerSystemDir(Class<?> serverMainNailClass, Path scalaCompileServerSystemDir)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setupMethod = serverMainNailClass.getMethod("setupScalaCompileServerSystemDir", Path.class);
        setupMethod.setAccessible(true);
        setupMethod.invoke(null, scalaCompileServerSystemDir);
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

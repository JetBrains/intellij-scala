package org.jetbrains.plugins.scala.nailgun;

import com.facebook.nailgun.NGServer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class Utils {

    private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

    // Deliberately naming the class, not the Scala object, as modern Scala versions generate static forwarder methods.
    private static final String COMPILE_SERVER_TOKEN_OBJECT_NAME = "org.jetbrains.plugins.scala.server.CompileServerToken";

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

    /**
     * Reflectively calls `org.jetbrains.plugins.scala.server.CompileServerToken.tokenPathForPort`. This avoids
     * duplicating the code.
     */
    public static Path tokenPathForPort(ClassLoader classLoader, Path scalaCompileServerSystemDir, int port)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final Class<?> cls = Class.forName(COMPILE_SERVER_TOKEN_OBJECT_NAME, true, classLoader);
        final Method method = cls.getMethod("tokenPathForPort", Path.class, int.class);
        method.setAccessible(true);
        return (Path) method.invoke(null, scalaCompileServerSystemDir, port);
    }

    private Utils() {

    }
}

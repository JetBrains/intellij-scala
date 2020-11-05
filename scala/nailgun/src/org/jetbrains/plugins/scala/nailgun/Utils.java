package org.jetbrains.plugins.scala.nailgun;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

public class Utils {

    private static final String SERVER_CLASS_NAME = "org.jetbrains.jps.incremental.scala.remote.Main";

    public static Class<?> loadAndSetupMainClass(ClassLoader classLoader, Path buildSystemDir)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> serverClass = classLoader.loadClass(SERVER_CLASS_NAME);
        Method setupMethod = serverClass.getMethod("setup", Path.class);
        setupMethod.setAccessible(true);
        setupMethod.invoke(null, buildSystemDir);
        return serverClass;
    }

    private Utils() {

    }
}

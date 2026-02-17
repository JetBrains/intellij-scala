package org.jetbrains.plugins.scala.nailgun;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * used in `org.jetbrains.plugins.scala.worksheet.server.NonServerRunner`.
 */
public class MainLightRunner {

    public static void main(String[] args) throws ReflectiveOperationException {
        if (args.length < 4) throw invalidUsageException();

        String classpathStr = args[0];
        Path scalaCompileServerSystemDir = Paths.get(args[1]);
        Path jpsBuildSystemDir = Paths.get(args[2]);
        String[] argsToDelegate = Arrays.copyOfRange(args, 3, args.length);
        URLClassLoader classLoader = NailgunRunner.constructClassLoader(classpathStr);
        runMainMethod(scalaCompileServerSystemDir, jpsBuildSystemDir, argsToDelegate, classLoader);
    }

    @SuppressWarnings({"SameParameterValue", "OptionalGetWithoutIsPresent"})
    private static void runMainMethod(Path scalaCompileServerSystemDir, Path jpsBuildSystemDir, String[] args, ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> mainClass = Utils.loadAndSetupServerMainNailClass(classLoader, scalaCompileServerSystemDir, jpsBuildSystemDir);
        Method mainMethod = Arrays.stream(mainClass.getDeclaredMethods()).filter(x -> x.getName().equals("main")).findFirst().get();
        mainMethod.invoke(null, (Object) args); // use as varargs, do not pass arra
    }

    private static IllegalArgumentException invalidUsageException() {
        String usage = "Usage: " + NailgunRunner.class.getSimpleName() +
                " [classpath] [scala-compile-server-system-dir] [jps-build-system-dir] [other args]";
        return new IllegalArgumentException(usage);
    }
}

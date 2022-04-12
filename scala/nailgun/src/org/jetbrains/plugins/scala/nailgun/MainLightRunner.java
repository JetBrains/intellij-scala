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
        if (args.length < 3) throw invalidUsageException();

        String classpathStr = args[0];
        Path scalaCompileServerSystemDir = Paths.get(args[1]);
        String[] argsToDelegate = Arrays.copyOfRange(args, 2, args.length);
        URLClassLoader classLoader = NailgunRunner.constructClassLoader(classpathStr);
        System.err.println();
        runMainMethod(scalaCompileServerSystemDir, argsToDelegate, classLoader);
    }

    @SuppressWarnings({"SameParameterValue", "OptionalGetWithoutIsPresent"})
    private static void runMainMethod(Path scalaCompileServerSystemDir, String[] args, ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> mainClass = Utils.loadAndSetupServerMainNailClass(classLoader, scalaCompileServerSystemDir);
        Method mainMethod = Arrays.stream(mainClass.getDeclaredMethods()).filter(x -> x.getName().equals("main")).findFirst().get();
        mainMethod.invoke(null, (Object) args); // use as varargs, do not pass arra
    }

    private static IllegalArgumentException invalidUsageException() {
        String usage = "Usage: " + NailgunRunner.class.getSimpleName() +
                " [classpath] [system-dir-path] [other args]";
        return new IllegalArgumentException(usage);
    }
}

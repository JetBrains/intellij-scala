package org.jetbrains.plugins.scala.nailgun;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * used in {@link org.jetbrains.plugins.scala.compiler.NonServerRunner}.
 */
public class MainLightRunner {

    public static final String CLASSPATH_ARG = "-classpath";

    public static void main(String[] args) throws ReflectiveOperationException {
        if (args.length < 3 || !args[0].equals(CLASSPATH_ARG))
            throw invalidUsageException();

        String classpathStr = args[1];
        String[] argsToDelegate = Arrays.copyOfRange(args, 2, args.length);
        URLClassLoader classLoader = NailgunRunner.constructClassLoader(classpathStr);
        runMainMethod(NailgunRunner.SERVER_CLASS_NAME, argsToDelegate, classLoader);
    }

    @SuppressWarnings({"SameParameterValue", "OptionalGetWithoutIsPresent"})
    private static void runMainMethod(String className, String[] args, ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> mainClass = classLoader.loadClass(className);
        Method mainMethod = Arrays.stream(mainClass.getDeclaredMethods()).filter(x -> x.getName().equals("main")).findFirst().get();
        mainMethod.invoke(null, (Object) args); // use as varargs, do not pass arra
    }

    private static IllegalArgumentException invalidUsageException() {
        String usage = "Usage: " + NailgunRunner.class.getSimpleName() + " " + CLASSPATH_ARG + " [classpath] [other args]";
        return new IllegalArgumentException(usage);
    }
}

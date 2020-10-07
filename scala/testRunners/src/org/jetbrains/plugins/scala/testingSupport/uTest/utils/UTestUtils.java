package org.jetbrains.plugins.scala.testingSupport.uTest.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.testingSupport.uTest.UTestPath;
import org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunExpectedError;
import utest.Tests;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.jetbrains.plugins.scala.testingSupport.uTest.utils.UTestErrorUtils.expectedError;

public class UTestUtils {

    /**
     * @return test node matching `override val tests: Tests` method of a test suite
     */
    @NotNull
    public static UTestPath findTestsNode(Class<?> clazz) throws UTestRunExpectedError {
        Method method = findTestDefinitionMethod(clazz);
        String className = clazz.getName();
        return new UTestPath(className, method);
    }

    public static Method findTestDefinitionMethod(Class<?> clazz) throws UTestRunExpectedError {
        Method method = Arrays.stream(clazz.getMethods())
                .filter(UTestUtils::isTestDefinitionMethod)
                .findAny()
                .orElse(null);
        if (method == null) throw expectedError("No tests definition method found in " + clazz.getName());
        else return method;
    }

    private static boolean isTestDefinitionMethod(Method m) {
        return m.getReturnType().equals(Tests.class)
                && m.getName().equals("tests")
                && m.getParameterTypes().length == 0
                && Modifier.isStatic(m.getModifiers());
    }

    public static Class<?> findClass(String classFqn) throws UTestRunExpectedError {
        try {
            return Class.forName(classFqn);
        } catch (ClassNotFoundException e) {
            throw expectedError(e.getClass().getSimpleName() + " for " + classFqn + ": " + e.getMessage());
        }
    }
}

package org.jetbrains.plugins.scala.testingSupport.uTest;

import java.lang.reflect.Method;
import java.util.*;

import static org.jetbrains.plugins.scala.testingSupport.uTest.UTestSuiteRunnerBase.getTreeClass;

public final class UTestRunnerArgs {

    final Map<String, Set<UTestPath>> classesToTests;

    public UTestRunnerArgs(Map<String, Set<UTestPath>> classesToTests) {
        this.classesToTests = new HashMap<>(classesToTests);
    }

    private static final String TEST_SUITE_KEY = "-s";
    private static final String TEST_NAME_KEY = "-testName";

    public static UTestRunnerArgs parse(String[] args) {
        Map<String, Set<UTestPath>> classesToTests = new HashMap<>();

        String currentClass = null;
        int argIdx = 0;
        while (argIdx < args.length) {
            switch (args[argIdx]) {
                case TEST_SUITE_KEY:
                    ++argIdx;
                    while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                        String className = args[argIdx];
                        classesToTests.put(className, new HashSet<>());
                        currentClass = className;
                        ++argIdx;
                    }
                    break;
                case TEST_NAME_KEY:
                    ++argIdx;
                    if (currentClass == null)
                        throw new RuntimeException("Failed to run tests: no suite class specified for test " + args[argIdx]);
                    while (!args[argIdx].startsWith("-")) {
                        String testName = args[argIdx];
                        UTestPath aTest = parseTestPath(currentClass, testName);
                        if (aTest != null) {
                            classesToTests.get(currentClass).add(aTest);
                        }
                        ++argIdx;
                    }
                    break;
                default:
                    ++argIdx;
                    break;
            }
        }
        return new UTestRunnerArgs(classesToTests);
    }

    private static UTestPath parseTestPath(String className, String argsString) {
        String[] nameArgs = argsString.split("\\\\");
        List<String> asList = Arrays.asList(nameArgs);
        List<String> testPath = asList.subList(1, asList.size());
        final Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException for " + className + ": " + e.getMessage());
            return null;
        }
        final Method method;
        try {
            method = clazz.getMethod(asList.get(0));
            assert (method.getReturnType().equals(getTreeClass()));
        } catch (NoSuchMethodException e) {
            System.out.println("NoSuchMethodException for " + asList.get(0) + " in " + className + ": " + e.getMessage());
            return null;
        }
        return new UTestPath(className, testPath, method);
    }
}

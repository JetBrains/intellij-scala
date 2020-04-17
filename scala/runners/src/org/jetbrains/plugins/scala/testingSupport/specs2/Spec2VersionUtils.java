package org.jetbrains.plugins.scala.testingSupport.specs2;

public class Spec2VersionUtils {

    private static final String CLASS_RUNNER_FQN = "org.specs2.runner.ClassRunner";

    private Spec2VersionUtils() {
    }

    public static boolean isSpecs2_3() throws Spec2RunExpectedError {
        Class<?> runnerClass;
        try {
            runnerClass = Class.forName(CLASS_RUNNER_FQN);
        } catch (ClassNotFoundException e) {
            throw new Spec2RunExpectedError("ClassNotFoundException for " + CLASS_RUNNER_FQN + ": " + e.getMessage());
        }
        return isSpecs2_3(runnerClass);
    }

    private static boolean isSpecs2_3(Class<?> runnerClass) {
        return runnerClass.isInterface();
    }
}

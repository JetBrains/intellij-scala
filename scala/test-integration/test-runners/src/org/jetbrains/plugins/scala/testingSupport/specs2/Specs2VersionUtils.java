package org.jetbrains.plugins.scala.testingSupport.specs2;

public class Specs2VersionUtils {

    private static final String CLASS_RUNNER_FQN = "org.specs2.runner.ClassRunner";

    private Specs2VersionUtils() {}

    /** Is Specs API compatible with Specs 3.x or 4.x? */
    public static boolean isSpecs2_3x_4x() throws Spec2RunExpectedError {
        Class<?> runnerClass;
        try {
            runnerClass = Class.forName(CLASS_RUNNER_FQN);
        } catch (ClassNotFoundException e) {
            throw new Spec2RunExpectedError("ClassNotFoundException for " + CLASS_RUNNER_FQN + ": " + e.getMessage());
        }
        return isSpecs2_3x_4x(runnerClass);
    }

    private static boolean isSpecs2_3x_4x(Class<?> runnerClass) {
        return runnerClass.isInterface();
    }
}

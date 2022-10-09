package org.jetbrains.plugins.scala.testingSupport.uTest.utils;

import org.jetbrains.plugins.scala.testingSupport.uTest.UTestRunExpectedError;

public class UTestErrorUtils {

    private UTestErrorUtils() {
    }

    public static UTestRunExpectedError expectedError(String message) {
        return new UTestRunExpectedError(message);
    }

    public static String errorMessage(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}

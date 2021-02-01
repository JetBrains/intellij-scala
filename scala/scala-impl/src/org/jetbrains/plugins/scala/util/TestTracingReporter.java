package org.jetbrains.plugins.scala.util;

import com.intellij.diagnostic.tracing.MethodTracerData;

import java.util.List;
import java.util.stream.Collectors;

public class TestTracingReporter {

    private TestTracingReporter() {

    }

    public static void report(Class<?> suitClass, String testCaseName, List<MethodTracerData> result) {
        if (!result.isEmpty()) {
            String stringResult = result.stream()
                    .map(data -> data.className + "." + data.methodName + "->" + data.invocationCount)
                    .collect(Collectors.joining(", "));
            System.out.println("[TRACING] " + suitClass.getName() + "." + testCaseName + ": " + stringResult);
        }
    }
}

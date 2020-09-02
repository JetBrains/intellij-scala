package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.util.*;

public class ScalaTestRunnerArgs {

    final Map<String, Set<String>> classesToTests;
    final boolean showProgressMessages;
    final List<String> otherArgs;

    public ScalaTestRunnerArgs(Map<String, Set<String>> classesToTests,
                               boolean showProgressMessages,
                               List<String> otherArgs) {
        this.classesToTests = classesToTests;
        this.showProgressMessages = showProgressMessages;
        this.otherArgs = otherArgs;
    }

    public static ScalaTestRunnerArgs parse(List<String> args) {
        Map<String, Set<String>> classesToTests = new HashMap<>();
        boolean showProgressMessages = true;
        List<String> otherArgs = new ArrayList<>();

        int argIdx = 0;
        String currentClass = null;
        while (argIdx < args.size()) {
            switch (args.get(argIdx)) {
                case "-s":
                    ++argIdx;
                    while (argIdx < args.size() && !args.get(argIdx).startsWith("-")) {
                        String className = args.get(argIdx);
                        classesToTests.put(className, new HashSet<>());
                        currentClass = className;
                        ++argIdx;
                    }
                    break;
                case "-testName":
                    if (currentClass == null)
                        throw new RuntimeException("Failed to run tests: no suite class specified for test " + args.get(argIdx));
                    ++argIdx;
                    String testNames = args.get(argIdx);
                    String testNamesUnescaped = TestRunnerUtil.unescapeTestName(testNames);
                    classesToTests.get(currentClass).add(testNamesUnescaped);
                    ++argIdx;
                    break;
                case "-showProgressMessages":
                    ++argIdx;
                    showProgressMessages = Boolean.parseBoolean(args.get(argIdx));
                    ++argIdx;
                    break;
                default:
                    otherArgs.add(args.get(argIdx));
                    ++argIdx;
                    break;
            }
        }

        return new ScalaTestRunnerArgs(
                classesToTests,
                showProgressMessages,
                otherArgs
        );
    }
}
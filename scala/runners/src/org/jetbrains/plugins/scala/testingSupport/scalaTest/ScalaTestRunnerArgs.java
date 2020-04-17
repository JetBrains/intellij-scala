package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.util.*;

public class ScalaTestRunnerArgs {

    final Map<String, Set<String>> classesToTests;
    final boolean showProgressMessages;
    final String reporterFqn;
    final List<String> otherArgs;

    public ScalaTestRunnerArgs(Map<String, Set<String>> classesToTests,
                               boolean showProgressMessages,
                               String reporterFqn,
                               List<String> otherArgs) {
        this.classesToTests = classesToTests;
        this.showProgressMessages = showProgressMessages;
        this.reporterFqn = reporterFqn;
        this.otherArgs = otherArgs;
    }

    public static ScalaTestRunnerArgs parse(String[] args) {
        HashMap<String, Set<String>> classesToTests = new HashMap<>();
        boolean showProgressMessages = true;
        String reporterFqn = null;
        ArrayList<String> otherArgs = new ArrayList<>();

        int argIdx = 0;
        String currentClass = null;
        while (argIdx < args.length) {
            switch (args[argIdx]) {
                case "-s":
                    ++argIdx;
                    while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                        String className = args[argIdx];
                        classesToTests.put(className, new HashSet<>());
                        currentClass = className;
                        ++argIdx;
                    }
                    break;
                case "-testName":
                    if (currentClass == null)
                        throw new RuntimeException("Failed to run tests: no suite class specified for test " + args[argIdx]);
                    ++argIdx;
                    String testNames = args[argIdx];
                    String testNamesUnescaped = TestRunnerUtil.unescapeTestName(testNames);
                    classesToTests.get(currentClass).add(testNamesUnescaped);
                    ++argIdx;
                    break;
                case "-showProgressMessages":
                    ++argIdx;
                    showProgressMessages = Boolean.parseBoolean(args[argIdx]);
                    ++argIdx;
                    break;
                case "-C":
                    ++argIdx;
                    reporterFqn = args[argIdx];
                    ++argIdx;
                    break;
                default:
                    otherArgs.add(args[argIdx]);
                    ++argIdx;
                    break;
            }
        }

        return new ScalaTestRunnerArgs(
                classesToTests,
                showProgressMessages,
                reporterFqn,
                otherArgs
        );
    }
}
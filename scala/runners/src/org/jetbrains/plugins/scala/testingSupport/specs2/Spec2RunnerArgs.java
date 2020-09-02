package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.util.*;

public class Spec2RunnerArgs {

    final Map<String, Set<String>> classesToTests;
    final boolean showProgressMessages;
    final List<String> otherArgs;

    public Spec2RunnerArgs(Map<String, Set<String>> classesToTests,
                           boolean showProgressMessages,
                           List<String> otherArgs) {
        this.classesToTests = classesToTests;
        this.showProgressMessages = showProgressMessages;
        this.otherArgs = otherArgs;
    }

    public static Spec2RunnerArgs parse(List<String> args) {
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
                        currentClass = args.get(argIdx);
                        classesToTests.put(currentClass, new HashSet<>());
                        ++argIdx;
                    }
                    break;
                case "-testName":
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

        return new Spec2RunnerArgs(classesToTests, showProgressMessages, otherArgs);
    }
}

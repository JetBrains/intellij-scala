package org.jetbrains.plugins.scala.testingSupport.scalaTest;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.util.*;

public class ScalaTestRunnerArgs {

    final SortedMap<String, Set<String>> classesToTests;
    final boolean showProgressMessages;
    final List<String> otherArgs;

    private static final String TEST_SUITE_KEY = "-s";
    private static final String TEST_NAME_KEY = "-testName";
    private static final String SHOW_PROGRESS_MESSAGES_KEY = "-showProgressMessages";

    public ScalaTestRunnerArgs(SortedMap<String, Set<String>> classesToTests,
                               boolean showProgressMessages,
                               List<String> otherArgs) {
        this.classesToTests = classesToTests;
        this.showProgressMessages = showProgressMessages;
        this.otherArgs = otherArgs;
    }

    public static ScalaTestRunnerArgs parse(List<String> args) {
        SortedMap<String, Set<String>> classesToTests = new TreeMap<>();
        boolean showProgressMessages = true;
        List<String> otherArgs = new ArrayList<>();

        String currentClass = null;
        int argIdx = 0;
        while (argIdx < args.size()) {
            switch (args.get(argIdx)) {
                case TEST_SUITE_KEY:
                    ++argIdx;
                    while (argIdx < args.size() && !args.get(argIdx).startsWith("-")) {
                        String className = args.get(argIdx);
                        classesToTests.put(className, new HashSet<>());
                        currentClass = className;
                        ++argIdx;
                    }
                    break;
                case TEST_NAME_KEY:
                    ++argIdx;
                    if (currentClass == null)
                        throw new RuntimeException("Failed to run tests: no suite class specified for test " + args.get(argIdx));
                    String testNames = args.get(argIdx);
                    String testNamesUnescaped = TestRunnerUtil.unescapeTestName(testNames);
                    classesToTests.get(currentClass).add(testNamesUnescaped);
                    ++argIdx;
                    break;
                case SHOW_PROGRESS_MESSAGES_KEY:
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
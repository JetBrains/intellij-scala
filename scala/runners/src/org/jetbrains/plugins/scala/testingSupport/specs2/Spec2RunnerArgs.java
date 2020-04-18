package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Spec2RunnerArgs {

    final HashMap<String, Set<String>> classesToTests;
    final boolean showProgressMessages;
    final ArrayList<String> otherArgs;

    public Spec2RunnerArgs(HashMap<String, Set<String>> classesToTests,
                           boolean showProgressMessages,
                           ArrayList<String> otherArgs) {
        this.classesToTests = classesToTests;
        this.showProgressMessages = showProgressMessages;
        this.otherArgs = otherArgs;
    }

    public static Spec2RunnerArgs parse(String[] args) {
        HashMap<String, Set<String>> classesToTests = new HashMap<>();
        boolean showProgressMessages = true;
        ArrayList<String> otherArgs = new ArrayList<>();

        int argIdx = 0;
        String currentClass = null;
        while (argIdx < args.length) {
            switch (args[argIdx]) {
                case "-s":
                    ++argIdx;
                    while (argIdx < args.length && !args[argIdx].startsWith("-")) {
                        currentClass = args[argIdx];
                        classesToTests.put(currentClass, new HashSet<>());
                        ++argIdx;
                    }
                    break;
                case "-testName":
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
                default:
                    otherArgs.add(args[argIdx]);
                    ++argIdx;
                    break;
            }
        }

        return new Spec2RunnerArgs(classesToTests, showProgressMessages, otherArgs);
    }
}

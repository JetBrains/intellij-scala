package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.ClassRunner$;
import org.specs2.runner.NotifierRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Alexander Podkhalyuzin
 */
public class Specs2Runner {

  private static final String REPORTER_FQN = JavaSpecs2Notifier.class.getName();
  // see org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier_spec2x
  private static final String REPORTER_2x_FQN = JavaSpecs2Notifier.class.getName() + "_spec2x";

  public static void main(String[] argsRaw) throws IllegalAccessException {
    final boolean isSpecs2_3x_4x;
    try {
      isSpecs2_3x_4x = Specs2VersionUtils.isSpecs2_3x_4x();
    } catch (Spec2RunExpectedError spec2RunExpectedError) {
      System.out.println(spec2RunExpectedError.getMessage());
      return;
    }

    Spec2RunnerArgs args = Spec2RunnerArgs.parse(TestRunnerUtil.preprocessArgsFiles(argsRaw));
    List<List<String>> runnerArgsList = toSpec2LibArgsList(args, isSpecs2_3x_4x);

    for (List<String> runnerArgs : runnerArgsList) {
      String[] runnerArgsArray = runnerArgs.toArray(new String[0]);
      if (isSpecs2_3x_4x) {
        runSpecs2_3x4x(runnerArgsArray);
      } else {
        JavaSpecs2Notifier.myShowProgressMessages = args.showProgressMessages;
        final JavaSpecs2Notifier notifier = new JavaSpecs2Notifier();
        runSpecs2_2x(runnerArgsArray, notifier);
      }
    }
    System.exit(0);
  }

  /**
   * @return raw arguments passed to Spec2 internal runner
   * org.specs2.runner.NotifierRunner arguments:
   * https://etorreborre.github.io/specs2/guide/SPECS2-3.0.1/org.specs2.guide.ArgumentsReference.html
   */
  private static List<String> toSpec2LibArgs(Spec2RunnerArgs args, boolean isSpecs2_3x_4x) {
    List<String> libArgs = new ArrayList<>(args.otherArgs);
    libArgs.add("-notifier");
    libArgs.add(isSpecs2_3x_4x ? REPORTER_FQN : REPORTER_2x_FQN);
    return libArgs;
  }

  /**
   * One each class should passed separately to Spec2 runner, so for single Spec2RunnerArgs
   * we generate several args sets which will be passed to Spec2 runner.
   */
  private static List<List<String>> toSpec2LibArgsList(Spec2RunnerArgs args, boolean isSpecs2_3x_4x) {
    List<String> commonArgs = toSpec2LibArgs(args, isSpecs2_3x_4x);
    return args.classesToTests
            .entrySet().stream()
            .map(entry -> {
              String className = entry.getKey();
              Set<String> tests = entry.getValue();

              List<String> runnerArgs = new ArrayList<>();
              runnerArgs.add(className);
              runnerArgs.addAll(commonArgs);
              if (!tests.isEmpty()) {
                runnerArgs.add("-ex");
                runnerArgs.add("\"\\A" + String.join("|", tests) + "\\Z\"");
              }
              return runnerArgs;
            })
            .collect(Collectors.toList());
  }

  private static void runSpecs2_2x(String[] runnerArgsArray, JavaSpecs2Notifier notifier) throws IllegalAccessException {

    try {
      NotifierRunner runner = new NotifierRunner(notifier);
      Method method = runner.getClass().getMethod("start", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      Throwable cause = e.getCause();
      String message = cause.getMessage();
      if (message != null && message.startsWith(specInstantiationMessage)) {
        System.out.println(message);
      }
    }
  }

  private final static String specInstantiationMessage = "can not create specification";

  private static void runSpecs2_3x4x(String[] runnerArgsArray) {
    runWithNotifierRunner(runnerArgsArray, true);
  }

  private static void runWithNotifierRunner(String[] runnerArgsArray, boolean verbose) {
    final String runnerFQN = "org.specs2.NotifierRunner";

    try {
      ClassRunner$ runner = ClassRunner$.MODULE$;
      Method method = runner.getClass().getMethod("run", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      if (verbose) {
        String className = e.getClass().getSimpleName();
        String message = "\n" + className + " for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n";
        System.out.println(message);
        e.printStackTrace(System.out);
      }
    }
  }
}

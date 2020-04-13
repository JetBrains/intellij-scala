package org.jetbrains.plugins.scala.testingSupport.specs2;

import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil;
import org.specs2.runner.ClassRunner$;
import org.specs2.runner.NotifierRunner;

import java.io.IOException;
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

  public static final String REPORTER_FQN = Specs2Notifier.class.getName();

  public static void main(String[] argsRaw) throws NoSuchMethodException, IllegalAccessException, IOException {
    final boolean isSpecs2_3;
    try {
      isSpecs2_3 = Spec2VersionUtils.isSpecs2_3();
    } catch (Spec2RunExpectedError spec2RunExpectedError) {
      System.out.println(spec2RunExpectedError.getMessage());
      return;
    }

    String[] argRawFixed  = TestRunnerUtil.getNewArgs(argsRaw);
    Spec2RunnerArgs args = Spec2RunnerArgs.parse(argRawFixed);
    List<List<String>> runnerArgsList = toSpec2LibArgsList(args, isSpecs2_3);

    Specs2Notifier.myShowProgressMessages = args.showProgressMessages;

    final Specs2Notifier notifier = new Specs2Notifier();

    for (List<String> runnerArgs : runnerArgsList) {
      String[] runnerArgsArray = runnerArgs.toArray(new String[0]);
      if (isSpecs2_3) {
        runSpecs2_new(runnerArgsArray);
      } else {
        runSpecs2_old(runnerArgsArray, notifier);
      }
    }
    System.exit(0);
  }

  /**
   * @return raw arguments passed to Spec2 internal runner
   * org.specs2.runner.NotifierRunner arguments:
   * https://etorreborre.github.io/specs2/guide/SPECS2-3.0.1/org.specs2.guide.ArgumentsReference.html
   */
  private static List<String> toSpec2LibArgs(Spec2RunnerArgs args, boolean isSpecs2_3) {
    List<String> libArgs = new ArrayList<>(args.otherArgs);
    if (!REPORTER_FQN.equals(args.reporterFqn)) {
      //don't duplicate the reporter
      libArgs.add("-notifier");
      libArgs.add(args.reporterFqn);
    }
    if (isSpecs2_3) {
      //there is a bug with specs2 v3.1+: notifier does not get passed properly through NotifierRunner
      libArgs.add("-notifier");
      libArgs.add(REPORTER_FQN);
    }
    return libArgs;
  }

  /**
   * One each class should passed separately to Spec2 runner, so for single Spec2RunnerArgs
   * we generate several args sets which will be passed to Spec2 runner.
   */
  private static List<List<String>> toSpec2LibArgsList(Spec2RunnerArgs args, boolean isSpecs2_3) {
    List<String> commonArgs = toSpec2LibArgs(args, isSpecs2_3);
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

  private static void runSpecs2_old(String[] runnerArgsArray, Specs2Notifier notifier) throws NoSuchMethodException, IllegalAccessException {
    boolean hasNoStartMethod = false;
    boolean startNotFound = false;

    NotifierRunner runner = new NotifierRunner(notifier);

    try {
      Method method = runner.getClass().getMethod("start", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      hasNoStartMethod = true;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      String message = cause.getMessage();
      if (message != null && message.startsWith(specInstantiationMessage)) {
        System.out.println(message);
        return;
      }
      hasNoStartMethod = true;
    }

    if (hasNoStartMethod) {
      try {
        MyNotifierRunner myNotifierRunner = new MyNotifierRunner(notifier);
        Method method = myNotifierRunner.getClass().getMethod("start", String[].class);
        method.invoke(myNotifierRunner, (Object) runnerArgsArray);
      } catch (NoClassDefFoundError | NoSuchMethodException | IllegalAccessException e) {
        String className = e.getClass().getSimpleName();
        String message = "\n" + className + " for 'Start' in MyNotifierRunner " + e.getMessage() + "\n";
        System.out.println(message);
        startNotFound = true;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        if (message != null && message.startsWith(specInstantiationMessage)) {
          System.out.println(message);
          return;
        }
        System.out.println("\nInvocationTargetException for 'Start' in MyNotifierRunner; cause: " + message + "\n");
        startNotFound = true;
      }
    }

    if (startNotFound) {
      try {
        Method method = runner.getClass().getMethod("main", String[].class);
        method.invoke(runner, (Object) runnerArgsArray);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        String message = cause.getMessage();
        if (message != null && message.startsWith(specInstantiationMessage)) {
          System.out.println(message);
        }
      }
    }
  }

  private final static String specInstantiationMessage = "can not create specification";

  private static void runSpecs2_new(String[] runnerArgsArray) {
    runWithNotifierRunner(runnerArgsArray, true);
  }

  private static void runWithNotifierRunner(String[] runnerArgsArray, boolean verbose) {
    final String runnerFQN = "org.specs2.NotifierRunner";

    try {
      ClassRunner$ runner = ClassRunner$.MODULE$;
//      NotifierRunner runner = new NotifierRunner(notifier);
      Method method = runner.getClass().getMethod("run", String[].class);
      method.invoke(runner, (Object) runnerArgsArray);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      if (verbose) {
        String className = e.getClass().getSimpleName();
        String message = "\n" + className + " for 'main' in " + runnerFQN + ": " + e.getMessage() + "\n";
        System.out.println(message);
      }
    }
  }
}

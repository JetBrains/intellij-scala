package org.jetbrains.plugins.scala.testingSupport.specs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.specs.Specification;
import org.specs.runner.NotifierRunner;
import org.specs.util.Classes$;
import scala.Option;
import scala.Some;
import scala.reflect.Manifest;
import scala.reflect.Manifest$;

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.05.2010
 */
public class JavaSpecsRunner {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("The first argument should be the specification class name");
      return;
    }
    ArrayList<String> classes  = new ArrayList<String>();
    String sysFilter = ".*";
    String exFilter = ".*";
    int i = 0;
    while (i < args.length) {
      if (args[i].startsWith("-sus:")) {
        sysFilter = args[i].substring(5);
        i = i + 1;
      } else if (args[i].equals("-s")) {
        i = i + 1;
        while (i < args.length && !args[i].startsWith("-")) {
          classes.add(args[i]);
          i = i + 1;
        }
      } else if (args[i].startsWith("-ex:")) {
        exFilter = args[i].substring(4);
        i = i + 1;
      } else {
        i = i + 1;
      }
    }

    Classes$ c = Classes$.MODULE$;
    Manifest$ m = Manifest$.MODULE$;
    Manifest specManifest = m.classType(Specification.class);

    // Trigger specs to print exception details if it can't instantiate a test, for example due to an exception in the constructor.
    System.setProperty("debugCreateObject", "");

    for (String clazz: classes) {
      Method method = findCreateObjectMethod(c);

      if (method == null) {
        System.out.println("Scala Plugin internal error: can't find createObject method in org.specs.util.Classes");
        return;
      }

      StringBuilder diagnostics = new StringBuilder();
      String testClassName = findTestClassName(clazz, diagnostics);

      if (testClassName == null) {
        System.out.println("\nScala Plugin internal error: test class not found: " + diagnostics);
      } else {
        try {
          Option<Specification> option = (Option<Specification>) method.invoke(c, testClassName, specManifest);
          if (!option.isEmpty()) {
            runTest(sysFilter, exFilter, option);
          } else {
            System.out.println("\nTest could not be instantiated.");
          }
        }
        catch(IllegalAccessException e) {
          System.out.println("Scala Plugin internal error: illegal access exception");
          e.printStackTrace();
        }
        catch(InvocationTargetException e) {
          System.out.println("Scala Plugin internal error: invocation target exception");
          e.printStackTrace();
        }
      }
    }
  }

  private static String findTestClassName(String clazz, StringBuilder diagnostics) {
    String testClassName = null;
    String objectName = clazz + "$";
    try {
      Class<?> aClass = JavaSpecsRunner.class.getClassLoader().loadClass(clazz);
      if (Specification.class.isAssignableFrom(aClass)) {
        testClassName = clazz;
      } else {
        diagnostics.append(clazz + " does not extend " + Specification.class.getName() + ". ");
      }
    } catch (ClassNotFoundException e) {
       // ignore
    }
    try {
      Class<?> aClass = JavaSpecsRunner.class.getClassLoader().loadClass(objectName);
      if (Specification.class.isAssignableFrom(aClass)) {
        testClassName = objectName;
      } else {
        diagnostics.append(clazz + " does not extend " + Specification.class.getName());
      }
    } catch (ClassNotFoundException e) {
      // ignore
    }
    return testClassName;
  }

  private static Method findCreateObjectMethod(Classes$ c) {
    Method method = null;
    try {
      method = c.getClass().getMethod("createObject", String.class, scala.reflect.ClassManifest.class);
    }
    catch(NoSuchMethodException e) {
      try {
        method = c.getClass().getMethod("createObject", String.class, scala.reflect.Manifest.class);
      }
      catch(NoSuchMethodException ignore) {}
    }
    return method;
  }

  private static void runTest(final String sysFilter, final String exFilter, Option<Specification> option) {
    try {
      // The previous approach of passing these filters with a NotifierRunner#{susFilterPattern, exampleFilterPattern} was not working.
      System.setProperty("sus", sysFilter);
      System.setProperty("example", exFilter);
      Specification s = option.get();
      NotifierRunner runner = new NotifierRunner(s, new JavaSpecsNotifier());
      runner.reportSpecs();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

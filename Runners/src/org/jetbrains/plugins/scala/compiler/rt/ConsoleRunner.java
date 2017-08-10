package org.jetbrains.plugins.scala.compiler.rt;

import scala.Some;
import scala.collection.mutable.ListBuffer;
import scala.collection.mutable.ListBuffer$;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import scala.tools.nsc.GenericRunnerSettings;
import scala.tools.nsc.interpreter.ILoop;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ConsoleRunner {
  public static void main(String[] args) throws IOException, InvocationTargetException, IllegalAccessException {
    String[] newArgs;
    if (args.length == 1 && args[0].startsWith("@")) {
      String arg = args[0];
      File file = new File(arg.substring(1));
      if (!file.exists())
        throw new java.io.FileNotFoundException(String.format("argument file %s could not be found", file.getName()));
      FileReader fileReader = new FileReader(file);
      StringBuilder buffer = new StringBuilder();
      while (true) {
        int i = fileReader.read();
        if (i == -1) break;
        char c = (char) i;
        if (c == '\r') continue;
        buffer.append(c);
      }
      newArgs = buffer.toString().split("[\n]");
    } else {
      newArgs = args;
    }
    ILoop interpreterLoop = new ILoop(new Some(new BufferedReader(new InputStreamReader(System.in))),
        new PrintWriter(System.out));
    Method[] methods = interpreterLoop.getClass().getMethods();
    for (Method method : methods) {
      if (method.getName().equals("name") && method.getParameterTypes().length == 1 &&
          method.getParameterTypes()[0].isArray()) {
        method.invoke(interpreterLoop, (Object[]) newArgs);
        return;
      }
    }
    GenericRunnerSettings settings = new GenericRunnerSettings(new AbstractFunction1<String, BoxedUnit>() {
      /** Apply the body of this function to the argument.
       *  @return the result of function application.
       */
      @Override
      public BoxedUnit apply(String v1) {
        return BoxedUnit.UNIT;
      }
    });
    ListBuffer<String> buffer = ListBuffer$.MODULE$.<String>empty();
    for (String newArg : newArgs) {
      buffer.$plus$eq(newArg);
    }
    settings.processArguments(buffer.toList(), true);
    interpreterLoop.process(settings);
  }
}

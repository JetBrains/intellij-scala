package org.jetbrains.plugins.scala.compiler.rt;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.2010
 */

public class FastScalacRunner {
  public static final String FSC_QUALIFIED_NAME = "scala.tools.nsc.CompileClient";

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("ScalacRunner usage: ScalacRunner args_for_scalac_file");
      return;
    }

    String fileName = args[0];
    File f = new File(fileName);
    if (!f.exists()) {
      System.err.println("ScalacRunner: args_for_scalac_file not found");
      return;
    }

    List<String> scalacArgs = new ArrayList<String>();
    BufferedReader reader = null;
    FileInputStream inputStream = null;
    try {
      inputStream = new FileInputStream(f);
      reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;
      do {
        line = reader.readLine();
        if (line != null) scalacArgs.add(line);
      } while (line != null);
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Scalac internal error: " + e.getMessage() + "\n" + Arrays.toString(e.getStackTrace()));
      return;
    } finally {
      try {
        if (reader != null) try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
          System.err.println("Scalac internal error: " + e.getMessage()+ "\n" + Arrays.toString(e.getStackTrace()));
        }
      } finally {
        f.delete();
      }
    }

    try {
      Class<?> scalacMain = Class.forName(FSC_QUALIFIED_NAME);
      Method method = scalacMain.getMethod("main0", String[].class);
      method.invoke(null, ((Object) scalacArgs.toArray(new String[scalacArgs.size()])));
    }
    catch (Throwable e) {
      Throwable cause = e.getCause();
      System.err.println("Scalac internal error: " + e.getClass() + " " + Arrays.toString(e.getStackTrace()) +
              (cause != null ? Arrays.toString(e.getCause().getStackTrace()) : ""));
      for (StackTraceElement element : e.getStackTrace()) {
        System.err.println(element);
      }
      while (cause != null) {
        System.err.println("Caused by " + cause);
        for (StackTraceElement element : cause.getStackTrace()) {
          System.err.println(element);
        }
        cause = cause.getCause();
      }
    }
  }
}

package org.jetbrains.plugins.scala.compiler.rt;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ScalacRunner {
  public static final String SCALAC_QUALIFIED_NAME = "scala.tools.nsc.Main";

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
      System.err.println("Scalac internal error: " + e.getMessage());
      return;
    } finally {
      try {
        if (reader != null) try {
          reader.close();
        } catch (IOException e) {
          System.err.println("Scalac internal error: " + e.getMessage());
        }
      } finally {
        f.delete();
      }
    }

    try {
      Class<?> scalacMain = Class.forName(SCALAC_QUALIFIED_NAME);
      Method method = scalacMain.getMethod("main", String[].class);
      method.invoke(null, (Object) scalacArgs.toArray(new String[scalacArgs.size()]));
    } catch (Exception e) {
      System.err.println("Scalac internal error: " + e.getMessage());
    }
  }
}

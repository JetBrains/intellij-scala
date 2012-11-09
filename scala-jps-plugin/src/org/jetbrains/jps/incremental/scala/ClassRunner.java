package org.jetbrains.jps.incremental.scala;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ClassRunner {
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new RuntimeException("Wrong arguments: " + Arrays.asList(args).toString());
    }

    File file = new File(args[1]);

    String[] lines;
    try {
      lines = readLinesFrom(file);
    } finally {
      if (file.delete()) {
        file.deleteOnExit();
      }
    }

    Class<?> scalacMain = Class.forName(args[0]);
    Method method = scalacMain.getMethod("main", String[].class);
    method.invoke(null, ((Object) lines));
  }

  private static String[] readLinesFrom(File file) throws IOException {
    List<String> lines = new LinkedList<String>();
    BufferedReader reader = new BufferedReader(new FileReader(file));

    try {
      String line;
      while (true) {
        line = reader.readLine();
        if (line == null) break;
        lines.add(line);
      }
    } finally {
      reader.close();
    }

    return lines.toArray(new String[lines.size()]);
  }
}

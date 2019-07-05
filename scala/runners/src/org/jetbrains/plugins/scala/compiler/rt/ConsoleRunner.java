package org.jetbrains.plugins.scala.compiler.rt;

import org.jetbrains.plugins.scala.testingSupport.MyJavaConverters;

import java.io.*;

public class ConsoleRunner {

  public static void main(String[] args) throws IOException {
    ILoopWrapper iLoopWrapper = ILoopWrapper.instance(
        new BufferedReader(new InputStreamReader(System.in)),
        new PrintWriter(System.out)
    );
    String[] newArgs = processArgs(args);
    iLoopWrapper.process(MyJavaConverters.asScala(newArgs));
  }

  private static String[] processArgs(String[] args) throws IOException {
    if (args.length == 1 && args[0].startsWith("@")) {
      String arg = args[0];
      File file = new File(arg.substring(1));
      if (!file.exists()) {
        throw new java.io.FileNotFoundException(String.format("argument file %s could not be found", file.getName()));
      }
      FileReader fileReader = new FileReader(file);
      StringBuilder buffer = new StringBuilder();
      while (true) {
        int i = fileReader.read();
        if (i == -1) break;
        char c = (char) i;
        if (c == '\r') continue;
        buffer.append(c);
      }
      return buffer.toString().split("[\n]");
    } else {
      return args;
    }
  }
}

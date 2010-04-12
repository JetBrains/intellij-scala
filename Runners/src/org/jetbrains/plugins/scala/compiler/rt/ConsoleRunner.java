package org.jetbrains.plugins.scala.compiler.rt;

import scala.Some;
import scala.tools.nsc.InterpreterLoop;

import java.io.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ConsoleRunner {
  public static void main(String[] args) throws IOException {
    String[] newArgs;
    if (args.length == 1 && args[0].startsWith("@")) {
      String arg = args[0];
      File file = new File(arg.substring(1));
      if (!file.exists())
        throw new java.io.FileNotFoundException(String.format("argument file %s could not be found", file.getName()));
      FileReader fileReader = new FileReader(file);
      StringBuffer buffer = new StringBuffer();
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
    (new InterpreterLoop(new Some(new BufferedReader(new InputStreamReader(System.in))),
        new PrintWriter(System.out))).main(newArgs);
  }
}

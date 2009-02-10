package org.jetbrains.plugins.scala.compiler.rt;

import scala.tools.nsc.Settings;
import scala.tools.nsc.InterpreterLoop;
import scala.tools.nsc.GenericRunnerCommand;
import scala.Some;
import scala.Function1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.02.2009
 */
public class ConsoleRunner {
  public static void main(String[] args) {
    GenericRunnerCommand command = new GenericRunnerCommand(ConsoleRunnerUtil.listOf(args), ConsoleRunnerUtil.getFunction());
    Settings settings = command.settings();
    settings.parseParams(join(args, " "), ConsoleRunnerUtil.getFunction());
    (new InterpreterLoop(new Some(new BufferedReader(new InputStreamReader(System.in))), new PrintWriter(System.out))).main(settings);
  }

  @NotNull
  public static String join(@NotNull final String[] strings, @NotNull final String separator) {
    return join(strings, 0, strings.length, separator);
  }

  @NotNull
  public static String join(@NotNull final String[] strings, int startIndex, int endIndex, @NotNull final String separator) {
    final StringBuilder result = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      if (i > startIndex) result.append(separator);
      result.append(strings[i]);
    }
    return result.toString();
  }
}

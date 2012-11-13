package org.jetbrains.jps.incremental.scala;

import org.jetbrains.jps.incremental.MessageHandler;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

/**
 * @author Pavel Fatin
 */
class MessageProducer {
  private final String myCompilerName;
  private MessageHandler myMessageHandler;

  MessageProducer(MessageHandler messageHandler, String compilerName) {
    myMessageHandler = messageHandler;
    myCompilerName = compilerName;
  }

  protected void error(String text) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.ERROR, text));
  }

  protected void error(String text, String sourcePath) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.ERROR, text, sourcePath));
  }

  protected void error(String text, String sourcePath, int line, int column) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.ERROR, text, sourcePath, -1, -1, -1, line, column));
  }

  protected void warn(String text, String sourcePath, int line, int column) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.WARNING, text, sourcePath, -1, -1, -1, line, column));
  }

  protected void info(String text) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.INFO, text));
  }

  protected void info(String text, String sourcePath) {
    myMessageHandler.processMessage(new CompilerMessage(myCompilerName, BuildMessage.Kind.INFO, text, sourcePath));
  }

  protected void progress(String text) {
    myMessageHandler.processMessage(new ProgressMessage(text));
  }
}

package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.process.{ProcessHandler, ProcessHandlerFactory}
import com.intellij.execution.runners.ExecutionEnvironment

class CbtComandLineState(task: String, workingDir: String, environment: ExecutionEnvironment)
  extends CommandLineState(environment) {

  override def startProcess(): ProcessHandler = {
    val factory = ProcessHandlerFactory.getInstance
    val commandLine = new GeneralCommandLine("cbt", task)
      .withWorkDirectory(workingDir)
    factory.createColoredProcessHandler(commandLine)
  }
}

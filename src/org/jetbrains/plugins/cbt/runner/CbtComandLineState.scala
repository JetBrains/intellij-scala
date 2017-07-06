package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.process.{ProcessEvent, ProcessHandler, ProcessHandlerFactory, ProcessListener}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key

class CbtComandLineState(task: String, workingDir: String, callback: Option[() => Unit], environment: ExecutionEnvironment)
  extends CommandLineState(environment) {

  override def startProcess(): ProcessHandler = {
    val factory = ProcessHandlerFactory.getInstance
    val commandLine = new GeneralCommandLine("cbt", task)
      .withWorkDirectory(workingDir)
    val hanlder = factory.createColoredProcessHandler(commandLine)
    hanlder.addProcessListener(new CbtProcessProcessListener)
    hanlder
  }

  private class CbtProcessProcessListener extends ProcessListener {
    override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

    override def startNotified(event: ProcessEvent): Unit = {}

    override def processTerminated(event: ProcessEvent): Unit = {
      callback.foreach(_.apply)
    }

    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {}
  }

}

package org.jetbrains.plugins.scala.script

import com.intellij.execution.configurations.{GeneralCommandLine, CommandLineState}
import com.intellij.execution.filters.{TextConsoleBuilder, TextConsoleBuilderFactory}

import com.intellij.execution.process.{OSProcessHandler, DefaultJavaProcessHandler, ProcessTerminatedListener}
import com.intellij.execution.runners.ExecutionEnvironment
import config.ScalaConfigUtils
import java.io.File

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.02.2009
 */

class ScalaScriptRunCommandLineState(configuration: ScalaScriptRunConfiguration, env: ExecutionEnvironment)
        extends CommandLineState(env){
  initConsoleBuilder
  def startProcess: OSProcessHandler = {
    val commandLine = createCommandLine
    val processHandler = new DefaultJavaProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private def createCommandLine: GeneralCommandLine = {
    val commandLine = new GeneralCommandLine
    val sdk = configuration.getScalaInstallPath
    var exePath = sdk.replace('\\', File.separatorChar)
    if (!exePath.endsWith(File.separator)) exePath += File.separator
    exePath += "bin" + File.separator + "scala.bat"
    commandLine.setExePath(exePath)
    commandLine.addParameter(configuration.getScriptPath)
    return commandLine
  }


  def initConsoleBuilder: Unit = {
    val consoleBuilder = TextConsoleBuilderFactory.getInstance.createBuilder(configuration.getProject());
    setConsoleBuilder(consoleBuilder);
  }

}
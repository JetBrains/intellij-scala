package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.process.{OSProcessHandler, ProcessHandler}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element

import scala.collection.JavaConversions._


class CbtRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {

  private def defaultWorkingDirectory = Option(project.getBaseDir).fold("")(_.getPath)

  private var task = ""

  override def getValidModules: util.Collection[Module] = List()

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new CbtRunConfigurationEditor()

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = {
    new CbtComandLineState(this, environment)
  }

  def getTask: String = task

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "task", task)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    task = JDOMExternalizer.readString(element, "task")
  }

  def apply(params: CbtRunConfigurationForm): Unit = {
    task = params.getTask
  }

  class CbtComandLineState(configuration: CbtRunConfiguration, environment: ExecutionEnvironment)
    extends CommandLineState(environment) {
    override def startProcess(): ProcessHandler = {
      val commandLine = new GeneralCommandLine("cbt", task)
        .withWorkDirectory(defaultWorkingDirectory)
      new OSProcessHandler(commandLine)
    }
  }
}

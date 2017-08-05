package org.jetbrains.plugins.cbt.runner

import java.util

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element

import scala.collection.JavaConversions._


class CbtRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {

  private var task = ""
  private var workingDir = defaultWorkingDirectory

  override def getValidModules: util.Collection[Module] = List()

  override def getConfigurationEditor: SettingsEditor[CbtRunConfiguration] =
    new CbtRunConfigurationEditor(project, this)

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = {
    new CbtComandLineState(task, false, workingDir, CbtProcessListener.Dummy, environment)
  }

  def getTask: String = task

  def getWorkingDir: String = workingDir

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "task", task)
    JDOMExternalizer.write(element, "workingDir", workingDir)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    task = JDOMExternalizer.readString(element, "task")
    workingDir = JDOMExternalizer.readString(element, "workingDir")
  }

  def apply(params: CbtRunConfigurationForm): Unit = {
    task = params.getTask
    workingDir = params.getWorkingDirectory
  }

  private def defaultWorkingDirectory = Option(project.getBaseDir).fold("")(_.getPath)
}

package org.jetbrains.plugins.cbt.runner

import java.util
import java.util.Collections

import com.intellij.execution.{BeforeRunTask, Executor}
import com.intellij.execution.configurations._
import com.intellij.execution.impl.UnknownBeforeRunTaskProvider
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.ui.ListUtil
import org.jdom.Element
import org.jetbrains.plugins.cbt._


class CbtRunConfiguration(val project: Project,
                          val configurationFactory: ConfigurationFactory,
                          val name: String)
  extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  private lazy val moduleManager = ModuleManager.getInstance(project)
  private var task = ""
  private var module: Module = _
  runReadAction {
    module = moduleManager.getSortedModules.last
  }

  override def getModules: Array[Module] = Array(module)

  override def getValidModules: util.Collection[Module] = util.Arrays.asList(getModules:_*)

  override def getConfigurationEditor: SettingsEditor[CbtRunConfiguration] =
    new CbtRunConfigurationEditor(project, this)

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
    new CbtCommandLineState(task, false, module.baseDir, CbtProcessListener.Dummy, environment)

  def getTask: String = task

  def getModule: Module = module

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "task", task)
    JDOMExternalizer.write(element, "moduleName", module.getName)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    task = JDOMExternalizer.readString(element,"task")
    module = {
      val moduleName = JDOMExternalizer.readString(element, "moduleName")
      Option(moduleName)
        .map(moduleManager.findModuleByName)
        .orNull
    }
  }

  override def getBeforeRunTasks: util.List[BeforeRunTask[_]] = {
    val unknownTask = new UnknownBeforeRunTaskProvider("unknown").createTask(this)
    Collections.singletonList(unknownTask)
  }
  def apply(params: CbtRunConfigurationForm): Unit = {
    task = params.getTask
    module = {
      val moduleName = params.getModuleName
      Option(moduleName)
        .map(moduleManager.findModuleByName)
        .orNull
    }
  }

}

package org.jetbrains.plugins.scala.script


import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.execution.configurations._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.Executor
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.{ModuleManager, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.SettingsEditor
import config.{ScalaConfigUtils, ScalaSDK}
import java.util.{Arrays, Collection}

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.02.2009
 */

class ScalaScriptRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {
  private var scriptPath = ""
  private var scriptArgs = ""

  def getScriptPath = scriptPath
  def getScriptArgs = scriptArgs
  def setScriptPath(s: String): Unit = scriptPath = s
  def setScriptArgs(s: String): Unit = scriptArgs = s

  def apply(params: ScalaScriptRunConfigurationForm) {
    setScriptArgs(params.getScriptArgs)
    setScriptPath(params.getScriptPath)
  }

  def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    new ScalaScriptRunCommandLineState(this, env)
  }


  def createInstance: ModuleBasedConfiguration[_ <: RunConfigurationModule] =
    new ScalaScriptRunConfiguration(getProject, getFactory, getName)

  def getValidModules: java.util.List[Module] = {
    val result = new ArrayBuffer[Module]

    val modules = ModuleManager.getInstance(getProject).getModules
    for (module <- modules) {
      val facetManager = FacetManager.getInstance(module)
      if (facetManager.getFacetByType(org.jetbrains.plugins.scala.config.ScalaFacet.ID) != null) {
        result += module
      }
    }
    return Arrays.asList(result.toArray: _*)
  }

  def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new ScalaScriptRunConfigurationEditor(project, this)

  def getSdk(): ScalaSDK = {
    val modules = getValidModules
    if (modules.size > 0) ScalaConfigUtils.getScalaSDKs(modules.get(0)).apply(0)
    else null
  }

  def getScalaInstallPath(): String = {
    val modules = getValidModules
    if (modules.size > 0) ScalaConfigUtils.getScalaInstallPath(modules.get(0))
    else ""
  }
}
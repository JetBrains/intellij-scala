package org.jetbrains.sbt.runner

import java.util

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.JDOMExternalizer
import org.jdom.Element
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.sbt.project.structure.SbtRunner
import org.jetbrains.sbt.settings.SbtSystemSettings

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * Run configuration of SBT tasks.
 */
class SbtRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {

  /**
   * Main class of SBT.
   */
  val MAIN_CLASS = "xsbt.boot.Boot"

  /**
   * List of task to execute in format of SBT.
   */
  private var tasks = ""

  /**
   * Extra java options.
   */
  private var javaOptions = "-Xms512M -Xmx1024M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"

  /**
   * Environment variables.
   */
  private val envirnomentVariables: java.util.Map[String, String] = new mutable.HashMap[String, String]()

  override def getValidModules: util.Collection[Module] = List()

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = {
    val state: SbtComandLineState = new SbtComandLineState(this, env)
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance.createBuilder(getProject))
    state
  }

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SbtRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, "tasks", getTasks)
    JDOMExternalizer.write(element, "vmparams", getJavaOptions)
    EnvironmentVariablesComponent.writeExternal(element, getEnvironmentVariables)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    tasks = JDOMExternalizer.readString(element, "tasks")
    javaOptions = JDOMExternalizer.readString(element, "vmparams")
    EnvironmentVariablesComponent.readExternal(element, envirnomentVariables)
  }

  def apply(params: SbtRunConfigurationForm): Unit = {
    tasks = params.getTasks
    javaOptions = params.getJavaOptions
    envirnomentVariables.clear()
    envirnomentVariables.putAll(params.getEnvironmentVariables)
  }


  def getTasks: String = tasks

  def getJavaOptions: String = javaOptions

  def getEnvironmentVariables = envirnomentVariables

  class SbtComandLineState(configuration: SbtRunConfiguration, envirnoment: ExecutionEnvironment)
          extends JavaCommandLineState(envirnoment) {

    def createJavaParameters(): JavaParameters = {
      val params: JavaParameters = new JavaParameters
      val jdk: Sdk = JavaParametersUtil.createProjectJdk(configuration.getProject, null)
      try {
        jdk.getSdkType match {
          case sdkType : AndroidSdkType =>
            envirnomentVariables.put("ANDROID_HOME", jdk.getSdkModificator.getHomePath)
          case _ => // do nothing
        }
      } catch {
        case _ : NoClassDefFoundError => // no android plugin, do nothing
      }
      params.setWorkingDirectory(project.getBaseDir.getPath)
      params.configureByProject(configuration.getProject, JavaParameters.JDK_ONLY, jdk)
      val sbtSystemSettings: SbtSystemSettings = SbtSystemSettings.getInstance(configuration.getProject)
      if (sbtSystemSettings.getCustomLauncherEnabled)
        params.getClassPath.add(sbtSystemSettings.getCustomLauncherPath)
      else
        params.getClassPath.add(SbtRunner.getDefaultLauncher)
      params.setMainClass(MAIN_CLASS)
      params.setEnv(envirnomentVariables)
      params.getVMParametersList.addParametersString(javaOptions)
      params.getProgramParametersList.addParametersString(tasks)
      params
    }

    override def ansiColoringEnabled(): Boolean = true
  }

}
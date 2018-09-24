package org.jetbrains.sbt.runner

import java.io.File
import java.util
import java.util.jar.JarFile

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations._
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.{ExecutionResult, Executor, OutputListener}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{JDOMExternalizer, Key}
import com.intellij.util.execution.ParametersListUtil
import org.jdom.Element
import org.jetbrains.android.sdk.AndroidSdkType
import org.jetbrains.plugins.scala.extensions.using
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.runner.SbtRunConfiguration._
import org.jetbrains.sbt.settings.SbtSettings

import scala.collection.JavaConverters._

/**
 * Run configuration of sbt tasks.
 */
class SbtRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule](name, new RunConfigurationModule(project), configurationFactory) {

  /**
   * List of task to execute in format of sbt.
   */
  protected var tasks: String = ""

  /**
   * Extra java options.
   */
  protected var javaOptions: String = "-Xms512M -Xmx1024M -Xss1M -XX:+CMSClassUnloadingEnabled"

  /**
   * Environment variables.
   */
  protected val environmentVariables: java.util.Map[String, String] = new java.util.HashMap[String, String]()

  protected var workingDirectory: String = defaultWorkingDirectory
  
  protected var isUsingSbtShell: Boolean = true

  private def defaultWorkingDirectory = Option(project.getBaseDir).fold("")(_.getPath)

  override def getValidModules: util.Collection[Module] = new java.util.ArrayList

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = 
    new SbtCommandLineState(preprocessTasks(), this, env)

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SbtRunConfigurationEditor(project, this)

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    JDOMExternalizer.write(element, TASKS_KEY, getTasks)
    JDOMExternalizer.write(element, VM_PARAMS_KEY, getJavaOptions)
    JDOMExternalizer.write(element, WORK_DIR_KEY, getWorkingDir)
    JDOMExternalizer.write(element, USE_SBT_SHELL_KEY, getUseSbtShell)
    EnvironmentVariablesComponent.writeExternal(element, getEnvironmentVariables)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    tasks = JDOMExternalizer.readString(element, TASKS_KEY)
    javaOptions = JDOMExternalizer.readString(element, VM_PARAMS_KEY)
    workingDirectory = JDOMExternalizer.readString(element, WORK_DIR_KEY)
    isUsingSbtShell = JDOMExternalizer.readBoolean(element, USE_SBT_SHELL_KEY)
    EnvironmentVariablesComponent.readExternal(element, environmentVariables)
  }

  def apply(params: SbtRunConfigurationForm): Unit = {
    tasks = params.getTasks
    javaOptions = params.getJavaOptions
    workingDirectory = params.getWorkingDir
    environmentVariables.clear()
    environmentVariables.putAll(params.getEnvironmentVariables)
    isUsingSbtShell = params.isUseSbtShell
  }

  def determineMainClass(launcherPath: String): String = {
    using(new JarFile(new File(launcherPath))) { jf =>
      val attributes = jf.getManifest.getMainAttributes
      Option(attributes.getValue("Main-Class")).getOrElse("xsbt.boot.Boot")
    }
  }

  def getUseSbtShell: Boolean = isUsingSbtShell
  
  def getTasks: String = tasks

  def getJavaOptions: String = javaOptions

  def getEnvironmentVariables: util.Map[String, String] = environmentVariables

  def getWorkingDir: String = if (StringUtil.isEmpty(workingDirectory)) defaultWorkingDirectory else workingDirectory
  
  protected def preprocessTasks(): String = if (!getUseSbtShell || getTasks.trim.startsWith(";")) getTasks else {
    val commands = ParametersListUtil.parse(getTasks, true).asScala
    if (commands.length == 1) commands.head else commands.mkString(";", " ;", "")
  }
}

object SbtRunConfiguration {
  private val TASKS_KEY = "tasks"
  private val VM_PARAMS_KEY = "vmparams"
  private val WORK_DIR_KEY = "workingDir"
  private val USE_SBT_SHELL_KEY = "useSbtShell"
}


class SbtCommandLineState(val processedCommands: String, val configuration: SbtRunConfiguration, environment: ExecutionEnvironment, 
                          private var listener: Option[String => Unit] = None) extends JavaCommandLineState(environment) {
  def getListener: Option[String => Unit] = listener
  
  def setListener(l: Option[String => Unit]): Unit = listener = l

  override def execute(executor: Executor, runner: ProgramRunner[_ <: RunnerSettings]): ExecutionResult = {
    val r = super.execute(executor, runner)
    listener.foreach(l => Option(r.getProcessHandler).foreach(_.addProcessListener(new OutputListener() {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = super.onTextAvailable(event, outputType)
    })))
    r
  }

  def determineMainClass(launcherPath: String): String = {
    using(new JarFile(new File(launcherPath))) { jf =>
      val attributes = jf.getManifest.getMainAttributes
      Option(attributes.getValue("Main-Class")).getOrElse("xsbt.boot.Boot")
    }
  }
  
  def createJavaParameters(): JavaParameters = {
    val environmentVariables = configuration.getEnvironmentVariables
    val params: JavaParameters = new JavaParameters
    val jdk: Sdk = JavaParametersUtil.createProjectJdk(configuration.getProject, null)

    try {
      jdk.getSdkType match {
        case _: AndroidSdkType =>
          environmentVariables.put("ANDROID_HOME", jdk.getSdkModificator.getHomePath)
        case _ => // do nothing
      }
    } catch {
      case _: NoClassDefFoundError => // no android plugin, do nothing
    }
    
    params.setWorkingDirectory(configuration.getWorkingDir)
    params.configureByProject(configuration.getProject, JavaParameters.JDK_ONLY, jdk)
    
    val sbtSystemSettings = SbtSettings.getInstance(configuration.getProject).getState
    if (sbtSystemSettings.getCustomLauncherEnabled) {
      params.getClassPath.add(sbtSystemSettings.getCustomLauncherPath)
      params.setMainClass(determineMainClass(sbtSystemSettings.getCustomLauncherPath))
    } else {
      val launcher = SbtUtil.getDefaultLauncher
      params.getClassPath.add(launcher)
      params.setMainClass(determineMainClass(launcher.getAbsolutePath))
    }
    
    params.setEnv(environmentVariables)
    params.getVMParametersList.addParametersString(configuration.getJavaOptions)
    params.getProgramParametersList.addParametersString(processedCommands)
    
    params
  }
}

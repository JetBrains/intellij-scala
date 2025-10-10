package org.jetbrains.sbt.runner

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations._
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.{EnvFilesOptions, ExecutionResult, Executor, OutputListener}
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.{JarManifestUtils, JdomExternalizerMigrationHelper}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.SbtExternalSystemManager
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path
import java.util
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

/**
 * Run configuration of sbt tasks.
 */
class SbtRunConfiguration(val project: Project, val configurationFactory: ConfigurationFactory, val name: String)
        extends ModuleBasedConfiguration[RunConfigurationModule,Element](name, new RunConfigurationModule(project), configurationFactory) with EnvFilesOptions {

  /**
   * List of task to execute in format of sbt.
   */
  @BeanProperty var tasks: String = ""

  /**
   * Extra java options.
   */
  @BeanProperty var vmparams: String = "-Xms512M -Xmx1024M -Xss1M"

  /**
   * Environment variables.
   */
  val environmentVariables: java.util.Map[String, String] = new java.util.HashMap[String, String]()

  @XCollection
  @BeanProperty
  var envFilePaths: java.util.List[String] = new util.ArrayList[String]()

  @BeanProperty var workingDir: String = defaultWorkingDirectory

  @BeanProperty var useSbtShell: Boolean = true

  private def defaultWorkingDirectory = Option(project.baseDir).fold("")(_.getPath)

  override def getValidModules: util.Collection[Module] = new java.util.ArrayList

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
    new SbtCommandLineState(preprocessTasks(), this, env)

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new SbtRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    workingDir = if (StringUtil.isEmpty(workingDir)) defaultWorkingDirectory else workingDir
    XmlSerializer.serializeInto(this, element)
    EnvironmentVariablesComponent.writeExternal(element, environmentVariables)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    XmlSerializer.deserializeInto(this, element)
    EnvironmentVariablesComponent.readExternal(element, environmentVariables)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("tasks")(tasks = _)
      helper.migrateString("vmparams")(vmparams = _)
      helper.migrateString("workingDir")(workingDir = _)
      helper.migrateBool("useSbtShell")(useSbtShell = _)
    }
  }

  def apply(params: SbtRunConfigurationForm): Unit = {
    tasks = params.getTasks
    vmparams = params.getJavaOptions
    workingDir = params.getWorkingDir
    environmentVariables.clear()
    environmentVariables.putAll(params.getEnvironmentVariables)
    envFilePaths.clear()
    envFilePaths.addAll(params.getEnvFilePaths)
    useSbtShell = params.isUseSbtShell
  }

  protected def preprocessTasks(): String = if (!useSbtShell || tasks.trim.startsWith(";")) tasks else {
    val commands = ParametersListUtil.parse(tasks, false).asScala
    if (commands.length == 1) commands.head else commands.mkString(";", " ;", "")
  }
}

class SbtCommandLineState(val processedCommands: String, val configuration: SbtRunConfiguration, environment: ExecutionEnvironment,
                          private var listener: Option[String => Unit] = None) extends JavaCommandLineState(environment) {
  def getListener: Option[String => Unit] = listener

  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val r = super.execute(executor, runner)
    listener.foreach(_ => Option(r.getProcessHandler).foreach(_.addProcessListener(new OutputListener() {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = super.onTextAvailable(event, outputType)
    })))
    r
  }

  def determineMainClass(launcherPath: String): String = {
    val jar = Path.of(launcherPath)
    JarManifestUtils.readManifestAttribute(jar, "Main-Class").getOrElse("xsbt.boot.Boot")
  }

  override def createJavaParameters(): JavaParameters = {
    val project = configuration.getProject
    val params: JavaParameters = new JavaParameters

    params.setWorkingDirectory(configuration.workingDir)

    val sbtExecutionSettings = SbtExternalSystemManager.executionSettingsFor(project)

    val customJdk = for {
      vmExecutablePath <- sbtExecutionSettings.getCustomVMExecutableOrWarn(project)
      // The java installation directory is two levels up.
      // See org.jetbrains.sbt.project.SbtExternalSystemManager.getVmExecutable
      javaHome = vmExecutablePath << 2
      if javaHome != null
      jdk  <- Option(ExternalSystemJdkUtil.findJdkInSdkTableByPath(javaHome.getAbsolutePath))
    } yield jdk

    val jdk = customJdk.getOrElse(JavaParametersUtil.createProjectJdk(project, null))
    params.configureByProject(project, JavaParameters.JDK_ONLY, jdk)

    val environmentVariables = new util.HashMap(configuration.environmentVariables)
    environmentVariables.putAll(configureEnvsFromFiles(configuration, true))
    params.setEnv(environmentVariables)

    val sbtSystemSettings = SbtSettings.getInstance(project).getState

    // One of these checks might be redundant.
    // Why do we need the customLauncherEnabled at all?
    if (sbtSystemSettings.customLauncherPath != null) {
      params.getClassPath.add(sbtSystemSettings.customLauncherPath)
      params.setMainClass(determineMainClass(sbtSystemSettings.customLauncherPath))
    } else {
      val launcher = SbtUtil.getDefaultLauncher
      params.getClassPath.add(launcher)
      params.setMainClass(determineMainClass(launcher.getAbsolutePath))
    }

    params.getVMParametersList.addParametersString(configuration.vmparams)
    params.getProgramParametersList.addParametersString(processedCommands)

    params
  }
}

package org.jetbrains.sbt.runner

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.util.EnvFilesUtilKt.configureEnvsFromFiles
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.execution.{EnvFilesOptions, ExecutionResult, Executor, OutputListener}
import com.intellij.openapi.application.ApplicationManager.getApplication
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
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.{JarManifestUtils, JdomExternalizerMigrationHelper}
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.{<<, SbtExternalSystemManager}
import org.jetbrains.sbt.settings.SbtSettings

import java.nio.file.Path
import java.util
import scala.beans.BeanProperty

/**
 * Run configuration of sbt tasks.
 */
class SbtRunConfiguration(
  val project: Project,
  val configurationFactory: ConfigurationFactory,
  val name: String
)  extends ModuleBasedConfiguration[RunConfigurationModule,Element](
  name,
  new RunConfigurationModule(project),
  configurationFactory
) with EnvFilesOptions {

  /**
   * List of task to execute in format of sbt.
   * Kept since 2026.2 only for compatibility with older IDEA versions.
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfiguration.migrateTasksToCommands]]
   * @see [[org.jetbrains.sbt.runner.SbtRunConfiguration.migrateCommandsToTasks]]
   * @note It could be removed later (after a few years), but doing this would break backward compatibility.
   */
  @BeanProperty var tasks: String = ""

  /**
   * Sbt commands to execute. The `commands` field in the Run Configuration UI is bound to this.
   * The value of this field is used directly when executing commands in both shell and non-shell modes.
   * No transformations are made at runtime.
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfiguration.migrateTasksToCommands]]
   * @see [[org.jetbrains.sbt.runner.SbtRunConfiguration.migrateCommandsToTasks]]
   */
  @BeanProperty var commands: String = ""

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

  private def defaultWorkingDirectory =
    if (getApplication == null || getApplication.isUnitTestMode) ""
    else Option(project.baseDir).fold("")(_.getPath)

  override def getValidModules: util.Collection[Module] = new java.util.ArrayList

  override def isBuildBeforeLaunchAddedByDefault: Boolean = false

  override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState =
    new SbtCommandLineState(commands, this, env)

  override def getConfigurationEditor: SettingsEditor[? <: RunConfiguration] = new SbtRunConfigurationEditor(project, this)

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)
    workingDir = if (StringUtil.isEmpty(workingDir)) defaultWorkingDirectory else workingDir

    /*
    Only overwrite `tasks` if the current `tasks` value cannot be migrated to the current commands.
    This means it is either a new Run Configuration or the user has modified the `commands` field.
    This condition prevents unnecessary format changes in configurations migrated from older plugin versions
    when no changes were made to the `commands` field.

    Without this condition, the following case would happen:
    1. An older IDEA saves: tasks = "task1 task2"
    2. The new IDEA loads: commands = "task1; task2" (migrated from tasks)
    3. New IDEA saves: `tasks` field is generated from commands so it becomes "task1; task2" — the format changed even though the user made no real edits.
    */
    val commandsWereModified = SbtRunConfiguration.migrateTasksToCommands(tasks) != commands
    if (commandsWereModified) {
      tasks = SbtRunConfiguration.migrateCommandsToTasks(commands)
    }

    XmlSerializer.serializeInto(this, element)
    EnvironmentVariablesComponent.writeExternal(element, environmentVariables)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    XmlSerializer.deserializeInto(this, element)
    EnvironmentVariablesComponent.readExternal(element, environmentVariables)

    // If `commands` has not been persisted yet but the legacy `tasks` field is present, the migration is required.
    if (commands.isEmpty && tasks.nonEmpty) {
      commands = SbtRunConfiguration.migrateTasksToCommands(tasks)
    }

    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("tasks")(tasks = _)
      helper.migrateString("vmparams")(vmparams = _)
      helper.migrateString("workingDir")(workingDir = _)
      helper.migrateBool("useSbtShell")(useSbtShell = _)
    }
  }

  def apply(params: SbtRunConfigurationForm): Unit = {
    commands = params.getTasks
    vmparams = params.getJavaOptions
    workingDir = params.getWorkingDir
    environmentVariables.clear()
    environmentVariables.putAll(params.getEnvironmentVariables)
    envFilePaths.clear()
    envFilePaths.addAll(params.getEnvFilePaths)
    useSbtShell = params.isUseSbtShell
  }
}

private object SbtRunConfiguration {
  import scala.jdk.CollectionConverters.*

  /**
   * Converts the old `tasks` format to the new `commands` format (semicolon-separated, no quotes).
   *
   * This is needed to preserve compatibility when a configuration created in an older plugin version is opened in the current one.
   * In older plugin versions, the space-separated `tasks` string was joined with semicolons and quotes were removed at runtime.
   * For example, the configuration `task1 task2` was transformed at runtime to `;task1; task2` and it worked correctly. Without
   * migrating from the old `tasks` format to the new `commands` format, `task1 task2` would not work in the current plugin version
   * because we no longer join the string with semicolons at runtime — we require semicolons explicitly. That is why the migration is needed.
   *
   * Examples (tasks -> commands):
   *  - `clean compile` → `clean; compile` (space-separated string joined with `;`)
   *  - `"clean; compile"` → `clean; compile` (outer quotes stripped)
   *  - `""` → `""`
   *
   * Rules:
   *  - Blank → returned as-is.
   *  - Contains `;` → the user already used the semicolon style, so only outer quotes are stripped if they are present
   *    (quotes are not allowed in the new format and break both shell and non-shell execution).
   *  - No `;` → join a space-separated string with `;`. The same happened at runtime in older plugin versions.
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfigurationMigrationTest#testMigrateTasksToCommands]]
   */
  def migrateTasksToCommands(tasks: String): String =
    if (tasks.isBlank) tasks
    else if (tasks.contains(";")) StringUtil.unquoteString(tasks)
    else ParametersListUtil.parse(tasks).asScala.mkString("; ")

  /**
   * Converts the new `commands` format back to the old `tasks` format.
   *
   * This is the reverse of [[migrateTasksToCommands]] and is needed to keep the persisted `tasks` field
   * in sync when the user edits the current `commands` field. To make the current `commands` value work
   * in older plugin versions, we wrap it in quotes whenever it contains spaces. This prevents the logic in older plugin versions from
   * splitting the string on spaces and joining with semicolons, which would cause problems when executing such a command.
   * For example, if the current commands value is `task1; task2` and we do not quote it, older plugin versions would turn it into `;task1; ;task2`
   * at runtime (in the sbt shell), breaking execution.
   *
   * Examples (commands → tasks):
   *  - `task1` → `task1` (returned as-is)
   *  - `task1; task1` → `"task1; task1"` (contains a space, so wrapped in quotes)
   *  - `add 1 2` → `"add 1 2"` (contains spaces, so wrapped in quotes)
   *  - `"task1; task1"` → `"task1; task1"` (already quoted, left unchanged)
   *
   * Rules:
   *  - Blank or already quoted → returned as-is.
   *  - Otherwise → wraps the string in quotes when it contains spaces.
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfigurationMigrationTest#testMigrateCommandsToTasks]]
   */
  def migrateCommandsToTasks(commands: String): String =
    if (commands.isBlank || StringUtil.isQuotedString(commands)) commands
    else ParametersListUtil.join(commands)
}

class SbtCommandLineState(
  val processedCommands: String,
  val configuration: SbtRunConfiguration,
  environment: ExecutionEnvironment,
  private var listener: Option[String => Unit] = None
) extends JavaCommandLineState(environment) {

  def getListener: Option[String => Unit] = listener

  override def execute(executor: Executor, runner: ProgramRunner[?]): ExecutionResult = {
    val r = super.execute(executor, runner)
    listener.foreach(_ => Option(r.getProcessHandler).foreach(_.addProcessListener(new OutputListener() {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[?]): Unit = super.onTextAvailable(event, outputType)
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
      jdk  <- Option(ExternalSystemJdkUtil.findJdkInSdkTableByPath(javaHome.toCanonicalPath.toString))
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
      val launcher = SbtUtil.defaultLauncherPath
      params.getClassPath.add(launcher)
      params.setMainClass(determineMainClass(launcher.toCanonicalPath.toString))
    }

    params.getVMParametersList.addParametersString(configuration.vmparams)
    params.getProgramParametersList.add(processedCommands)

    params
  }
}

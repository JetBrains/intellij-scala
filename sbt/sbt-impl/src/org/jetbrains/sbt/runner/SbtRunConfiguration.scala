package org.jetbrains.sbt.runner

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.{EnvFilesOptions, Executor}
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

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
   * How migration works:
   *
   * An input whose trimmed form starts with `;` is returned as-is. The old runtime skipped
   * all processing for such inputs (https://github.com/JetBrains/intellij-scala/commit/45f24a440b9a10bd1a8c1ff49616647e3513f93a),
   * and the current runtime passes `commands` to sbt unchanged, so no migration is needed.
   *
   * Otherwise, the old `tasks` string is split on unquoted whitespace with `ParametersListUtil.parse` (the same as it was done in the past),
   * which also drops real quotes and unescapes inner `\"`. The resulting tokens are then re-joined, and the separator
   * is chosen based on whether the original `tasks` string contains an unquoted `;`. If it does, the user already used
   * the proper `;`-separated format, so we join the tokens with a space; otherwise we join them with `;`, as in the past.
   *
   * Examples (tasks -> commands):
   *  - `;echo "foo"` → `;echo "foo"` (leading `;`, returned as-is)
   *  - `clean compile` → `clean; compile` (no `;`: joined with `;`)
   *  - `"clean; compile"` → `clean; compile` (outer quotes stripped)
   *  - `"echo \"fo;o\""` → `echo "fo;o"` (outer quotes stripped, inner escaped quotes unescaped)
   *  - `clean "echo \"a;b\""` → `clean; echo "a;b"` (the only `;` is quoted, so we join pieces with `;`)
   *  - `""` → `""`
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfigurationMigrationTest#testMigrateTasksToCommands]]
   */
  def migrateTasksToCommands(tasks: String): String =
    if (tasks.isBlank || tasks.trim.startsWith(";")) tasks
    else {
      // `splitHonorQuotes` honors single quotes, so for an input whose only semicolons are
      // inside single quotes, it returns a single element and `;` is chosen as the separator.
      // This should not be harmful:
      // - `;` is the separator used by the old `tasks` processing, so the migrated string matches the previous behavior
      // - sbt does not honor single quotes, so a command like this is most likely invalid anyway
      val containsUnquotedSemicolon = StringUtil.splitHonorQuotes(tasks, ';').size() > 1
      val sep = if containsUnquotedSemicolon then " " else "; "
      ParametersListUtil.parse(tasks).asScala.mkString(sep)
    }

  /**
   * Converts the new `commands` format back to the old `tasks` format.
   *
   * This is the reverse of [[migrateTasksToCommands]] and is needed to keep the persisted `tasks` field
   * in sync when the user edits the current `commands` field. Older plugin versions preprocessed the `tasks`
   * value at runtime with `ParametersListUtil.parse`, which splits on unquoted spaces (dropping quotes) and then
   * force-joins the resulting parts with semicolons. So a value with an unquoted space (e.g. `task1; task2`) was turned
   * at runtime into `;task1; ;task2` (unless it was wrapped in quotes).
   *
   * The goal of this migration is to wrap in quotes any `commands` value that contains an unquoted space, so it
   * keeps behaving the same under the old plugin versions. A value whose spaces are already inside quotes (e.g.
   * `task1;"add 1 2"`), or that is a single word without any spaces (e.g. `task1`), is kept as-is, because the old
   * runtime's `parse` already treats it as a single command.
   *
   * Examples (commands → tasks):
   *  - `task1` → `task1` (returned as-is)
   *  - `task1; task1` → `"task1; task1"` (unquoted space, so wrapped in quotes)
   *  - `add 1 2` → `"add 1 2"` (unquoted spaces, so wrapped in quotes)
   *  - `task1;"add 1 2"` → `task1;"add 1 2"` (spaces only inside quotes, returned as-is)
   *  - `"task1; task1"` → `"task1; task1"` (already quoted, returned as-is)
   *
   * Rules:
   *  - Blank or already quoted → returned as-is.
   *  - Contains an unquoted space → wrapped in quotes via `ParametersListUtil.join`.
   *  - Otherwise → returned as-is.
   *
   * @see [[org.jetbrains.sbt.runner.SbtRunConfigurationMigrationTest#testMigrateCommandsToTasks]]
   */
  def migrateCommandsToTasks(commands: String): String =
    if (commands.isBlank || StringUtil.isQuotedString(commands)) commands
    else {
      // `keepEmptyParameters = true` keeps the input untrimmed so edge whitespace (e.g. `  task1  `) also
      // counts as an unquoted space and gets wrapped.
      val shouldJoin = ParametersListUtil.parse(commands, /*keepQuotes*/ false, /*supportSingleQuotes*/ false, /*keepEmptyParameters*/ true).size > 1
      if (shouldJoin)
        ParametersListUtil.join(commands)
      else
        commands
    }
}
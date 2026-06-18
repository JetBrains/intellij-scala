package org.jetbrains.sbt
package project.settings

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.jetbrains.jps.incremental.scala.remote.SerializablePath
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.sbt.process.options.parsing.model.MalformedSbtOption
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtExecutionSettings.ConfigureJdkAction

import java.nio.file.Path

// TODO: add cross-references about some other settings-like entiites
/**
 * @inheritdoc
 *
 * @note [[SerializablePath]] is used in places instead of [[Path]] because we need to guarantee
 *       that this class is [[Serializable]].
 */
class SbtExecutionSettings(
  val realProjectPath: String,
  val vmExecutable: SerializablePath,
  val vmOptions: Seq[String],
  val sbtOptions: SbtExecutionSettings.SbtOptions,
  val hiddenDefaultMaxHeapSize: JvmMemorySize,
  val customLauncher: Option[SerializablePath],
  val customSbtStructureFile: Option[SerializablePath],
  val jdk: Option[String],
  val resolveClassifiers: Boolean,
  val resolveSbtClassifiers: Boolean,
  val useShellForImport: Boolean,
  val shellDebugMode: Boolean,
  val preferScala2: Boolean,
  val userSetEnvironment: Map[String, String],
  val passParentEnvironment: Boolean,
  val useSeparateCompilerOutputPaths: Boolean,
  val separateProdTestSources: Boolean,
  val generateManagedSourcesDuringProjectSync: Boolean,
  val sbtVersion: SbtVersion
) extends ExternalSystemExecutionSettings {

  /** If a custom VM executable is configured, return it. If it's not a valid path, warn the user. */
  def getCustomVMExecutableOrWarn(project: Project): Option[Path] = {
    val vmExecPath = Option(vmExecutable).map(_.toPath)
    vmExecPath match {
      case Some(path) =>
        if (path.isRegularFile)
          Some(path)
        else {
          showNoJreFoundNotification(project, path)
          None
        }
      case _ =>
        None
    }
  }

  private def showNoJreFoundNotification(project: Project, vmPath: Path): Unit = {
    val vmPathCanonical = vmPath.toCanonicalPath.toString
    val notification: Notification = ScalaNotificationGroups.sbtShell.createNotification(
      SbtBundle.message("sbt.shell.no.jre.found.at.path", vmPathCanonical),
      NotificationType.WARNING
    )
    notification.addAction(new ConfigureJdkAction(project))
    notification.notify(project)
  }
}

object SbtExecutionSettings {
  case class SbtOptions(
    options: Seq[String],
    malformedOptions: Seq[MalformedSbtOption] = Seq.empty,
  )

  object SbtOptions {
    val empty: SbtOptions = SbtOptions(Seq.empty)
  }

  private final class ConfigureJdkAction(project: Project) extends NotificationAction(SbtBundle.message("sbt.shell.configure.sbt.jvm")) {
    override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
      // External system handles the Configurable name for sbt settings
      ShowSettingsUtil.getInstance().showSettingsDialog(project, SbtProjectSystem.Id.getReadableName)
      notification.expire()
    }
  }
}

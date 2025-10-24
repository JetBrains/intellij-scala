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
import org.jetbrains.sbt.project.SbtProjectSystem

import java.nio.file.Path

// TODO: add cross-references about some other settings-like entiites
/**
 * @inheritdoc
 *
 * @note [[SerializablePath]] is used in places instead of [[Path]] because we need to guarantee
 *       that this class is [[Serializable]].
 */
class SbtExecutionSettings(val realProjectPath: String,
                           val vmExecutable: SerializablePath,
                           val vmOptions: Seq[String],
                           val sbtOptions: Seq[String],
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

  /** If a custom VM executable is configured, return it. If it's not a valid path, warn user. */
  def getCustomVMExecutableOrWarn(project: Project): Option[Path] =
    Option(vmExecutable).map(_.toPath).filter { path =>
      if (path.isRegularFile) true
      else {
        val badCustomVMNotification =
          ScalaNotificationGroups.sbtShell
            .createNotification(SbtBundle.message("sbt.shell.no.jre.found.at.path", vmExecutable.toPath.toCanonicalPath.toString), NotificationType.WARNING)

        val configureSbtAction = new NotificationAction(SbtBundle.message("sbt.shell.configure.sbt.jvm")) {

          override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
            // External system handles the Configurable name for sbt settings
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SbtProjectSystem.Id.getReadableName)
            notification.expire()
          }
        }
        badCustomVMNotification.addAction(configureSbtAction)
        badCustomVMNotification.notify(project)
        false
      }
    }
}

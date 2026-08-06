package org.jetbrains.bsp.protocol

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.project.BspExternalSystemConfigurable
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.bsp.project.importing.experimental.GenerateBspConfig
import org.jetbrains.bsp.protocol.BspConfigRegeneration.RegenerationReason
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.ref.WeakReference

/**
 * Project-level service that tracks displayed BSP notifications to prevent duplicates.
 * Only one notification can be active at a time for a project.
 *
 * See #SCL-20865, SCL-24961
 */
@Service(Array(Service.Level.PROJECT))
final class BspConnectionFileNotificationService(project: Project) {

  // The approach taken from org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications
  private val notifications: ConcurrentHashMap[String, WeakReference[Notification]] =
    new ConcurrentHashMap[String, WeakReference[Notification]]()

  private val RegenerateBspConnectionFileNotificationId = "bsp.regenerate.connection.file"
  private val ConfigChangedNotificationId = "bsp.configuration.file.changed"

  /** Shows a notification to regenerate the BSP connection file if not already shown. */
  def showRegenerateBspConnectionFileNotification(base: Path): Unit = {
    if (!canShow(RegenerateBspConnectionFileNotificationId)) return

    val RegenerateFileAndReloadAction = new NotificationAction(BspBundle.message("regenerate.file.and.reload")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        generateBspCommunicationFile(base)
        refreshProject()

        notification.hideBalloon()
      }
    }
    val RegenerateFileAction = new NotificationAction(BspBundle.message("regenerate.file")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        generateBspCommunicationFile(base)

        notification.hideBalloon()
      }
    }

    val notification = BSP.NotificationGroup
      .createNotification(
        BspBundle.message("unable.to.read.bsp.connection.file"),
        NotificationType.WARNING
      )
      .addAction(RegenerateFileAndReloadAction)
      .addAction(RegenerateFileAction)
      // Without a custom displayId, if "Don't show again" is clicked for this notification, it will apply to all
      // notifications in the BSP group instead of just this specific one.
      .setDisplayId(RegenerateBspConnectionFileNotificationId)

    notification.notify(project)
    registerNotification(RegenerateBspConnectionFileNotificationId, notification)
  }

  def showConfigChangedNotification(workspace: Path, reason: RegenerationReason): Unit = {
    if (!canShow(ConfigChangedNotificationId)) return

    val settingsAction = new NotificationAction(BspBundle.message("bsp.protocol.disable.in.settings")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        ShowSettingsUtilImplExt.showSettingsDialog(
          project,
          classOf[BspExternalSystemConfigurable],
          BspBundle.message("bsp.protocol.auto.generate.config")
        )
        notification.expire()
      }
    }
    val dontAskAction = new NotificationAction(BspBundle.message("bsp.protocol.dont.show.again")) {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        notification.setDoNotAskFor(project)
        notification.expire()
      }
    }

    val configFiles = BspConnectionConfig.workspaceConfigurationFiles(workspace)
    val changedFileNames =
      if (configFiles.size == 1) workspace.relativize(configFiles.head).toString
      else ".bsp/*.json"

    val contentMessage = reason match {
      case RegenerationReason.BeforeServerStartup =>
        BspBundle.message("bsp.protocol.config.file.changed.content", changedFileNames)
      case RegenerationReason.ServerFailure =>
        BspBundle.message("bsp.protocol.config.file.changed.content.server.failure", changedFileNames)
    }

    val actions = reason match {
      case RegenerationReason.BeforeServerStartup =>
        Seq(settingsAction, dontAskAction)
      case RegenerationReason.ServerFailure =>
        Seq(dontAskAction)
    }

    val notification = BSP.NotificationGroup
      .createNotification(
        BspBundle.message("bsp.protocol.config.file.regenerated"),
        contentMessage,
        NotificationType.INFORMATION
      )
      .setDisplayId(ConfigChangedNotificationId)
      .addActions(actions.asJava)

    notification.notify(project)
    registerNotification(ConfigChangedNotificationId, notification)
  }

  private def generateBspCommunicationFile(base: Path): Unit = {
    val generateBspConfig = new GenerateBspConfig(project, base)
    generateBspConfig.runSynchronously()
  }

  private def refreshProject(): Unit = {
    // We save all documents because there is a possible case that there is an external system config file changed inside the ide
    FileDocumentManager.getInstance.saveAllDocuments()
    val systemId = BSP.ProjectSystemId

    //can't call async version `confirmLoadingUntrustedProjectAsync` from Scala (or Java)
    //because it uses Kotlin coroutines
    val confirmed = ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(project, systemId)
    if (confirmed) {
      ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, systemId))
    }
  }

  /**
   * Checks if a notification with the given id can be shown.
   * Updates the cache by removing expired/disposed notifications.
   */
  private def canShow(notificationId: String): Boolean = {
    updateCache(notificationId)
    !notifications.containsKey(notificationId)
  }

  /** Registers a notification in the cache and sets up automatic cleanup. */
  private def registerNotification(notificationId: String, notification: Notification): Unit = {
    notifications.put(notificationId, WeakReference(notification))
    notification.whenExpired(() => notifications.remove(notificationId))
  }

  private def updateCache(notificationId: String): Unit = {
    val ref = notifications.get(notificationId)
    Option(ref).flatMap(_.get) match {
      case Some(notification) =>
        val balloon = notification.getBalloon
        if (balloon == null || balloon.isDisposed) {
          notifications.remove(notificationId)
        }
      case None =>
        notifications.remove(notificationId)
    }
  }
}

object BspConnectionFileNotificationService {
  def getInstance(project: Project): BspConnectionFileNotificationService =
    project.getService(classOf[BspConnectionFileNotificationService])
}

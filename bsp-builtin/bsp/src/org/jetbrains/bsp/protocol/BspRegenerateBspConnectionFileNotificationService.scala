package org.jetbrains.bsp.protocol

import com.intellij.notification.{Notification, NotificationAction, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.{BSP, BspBundle}
import org.jetbrains.bsp.project.importing.experimental.GenerateBspConfig

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import scala.ref.WeakReference

/**
 * Project-level service that tracks displayed BSP regenerate connection notifications to prevent duplicates.
 * Only one notification can be active at a time for a project.
 *
 * See #SCL-20865, SCL-24961
 */
@Service(Array(Service.Level.PROJECT))
final class BspRegenerateBspConnectionFileNotificationService(project: Project) {

  // The approach taken from org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtNotifications
  private val currentNotification: AtomicReference[Option[WeakReference[Notification]]] =
    new AtomicReference[Option[WeakReference[Notification]]](None)

  /** Shows a notification to regenerate the BSP connection file if not already shown. */
  def showRegenerateBspConnectionFileNotification(base: Path): Unit = {
    if (!canShow) return

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

    notification.notify(project)
    registerNotification(notification)
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
   * Checks if a notification can be shown.
   * Updates the cache by removing expired/disposed notifications.
   */
  private def canShow: Boolean = {
    updateCache()
    currentNotification.get().isEmpty
  }

  /** Registers a notification in the cache and sets up automatic cleanup. */
  private def registerNotification(notification: Notification): Unit = {
    currentNotification.set(Some(WeakReference(notification)))
    notification.whenExpired(() => currentNotification.set(None))
  }

  private def updateCache(): Unit =
    currentNotification.get() match {
      case Some(WeakReference(notification)) =>
        val balloon = notification.getBalloon
        if (balloon == null || balloon.isDisposed) {
          currentNotification.set(None)
        }
      case _ =>
        currentNotification.set(None)
    }
}

object BspRegenerateBspConnectionFileNotificationService {
  def getInstance(project: Project): BspRegenerateBspConnectionFileNotificationService =
    project.getService(classOf[BspRegenerateBspConnectionFileNotificationService])
}

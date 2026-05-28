package org.jetbrains.sbt.project

import com.intellij.build.issue.{BuildIssue, BuildIssueQuickFix}
import com.intellij.notification.{Notification, NotificationAction, NotificationGroupManager, NotificationType}
import com.intellij.openapi.actionSystem.{AnActionEvent, DataContext}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.build.{BuildReporter, ExternalSystemNotificationReporter}
import org.jetbrains.plugins.scala.settings.ShowSettingsUtilImplExt
import org.jetbrains.sbt.{SbtBundle, SbtUtil}
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.settings.SbtExternalSystemConfigurable

import java.util.List as JList
import java.util.concurrent.CompletableFuture
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.SeqHasAsJava

private object LegacyModulesLayoutNotifications {
  class LegacyModulesLayoutNotificationListener(project: Project) extends SbtProjectDataImportListener(project) {
    @volatile private var activeNotification: Notification = uninitialized

    // Expires the existing notification when an import starts with the new modules layout
    override def onImportStarted(projectPath: String): Unit = {
      if (!isListenerAllowed(projectPath) || project.isDisposed) return

      if (isMainTestModulesEnabled(projectPath) && isNotificationVisible) {
        activeNotification.expire()
      }
    }

    override def onImportFinished(projectPath: String): Unit = {
      if (!isListenerAllowed(projectPath) || project.isDisposed) return

      val isNewModeEnabled = isMainTestModulesEnabled(projectPath, defaultValue = true)
      if (!isNewModeEnabled && !isNotificationVisible) {
        Option(activeNotification).foreach(_.expire())
        activeNotification = createNotification()
        activeNotification.notify(project)
      }
    }

    private def isNotificationVisible: Boolean = {
      // Checking if the notification is expired isn't enough because when the user closes it manually,
      // the balloon disappears but the notification isn't marked as expired
      activeNotification != null && activeNotification.getBalloon != null
    }

    /**
     * @param projectPath  the external system project root path
     * @param defaultValue the default value to return if [[SbtProjectSettings]] for the given `projectPath` is not found
     */
    private def isMainTestModulesEnabled(projectPath: String, defaultValue: Boolean = false): Boolean = {
      val sbtProjectSettings = SbtProjectSettings.`for`(project, projectPath)
      sbtProjectSettings.map(_.separateProdAndTestSources).getOrElse(defaultValue)
    }

    private def createNotification(): Notification = {
      val group = NotificationGroupManager.getInstance().getNotificationGroup("sbt.legacy.modules.layout")
      val notification = group.createNotification(
        SbtBundle.message("sbt.legacy.modules.layout.notification.title"),
        SbtBundle.message("sbt.legacy.modules.layout.notification.content"),
        NotificationType.WARNING
      )

      val settingsAction = new NotificationAction(SbtBundle.message("open.sbt.project.settings")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit =
          ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], SbtBundle.message("separate.prod.test.modules"))
      }

      val readMoreAction = new NotificationAction(SbtBundle.message("separate.prod.test.modules.link.text")) {
        override def actionPerformed(e: AnActionEvent, notification: Notification): Unit =
          SbtUtil.openSeparateMainTestModulesBlogPost()
      }

      notification.addActions(List(settingsAction, readMoreAction).asJava)
    }
  }

  def warnForBuildToolWindow(buildReporter: BuildReporter): Unit =
    buildReporter match {
      case esReporter: ExternalSystemNotificationReporter =>
        val buildIssue = new BuildIssue {
          override def getTitle: String = SbtBundle.message("sbt.legacy.modules.layout.build.tool.window.title")

          override def getDescription: String = SbtBundle.message("sbt.legacy.modules.layout.build.tool.window.details", OpenMainTestModulesSettingsQuickFix.ID, SbtUtil.SeparateMainTestModulesBlogPostLink)

          override def getQuickFixes: JList[BuildIssueQuickFix] = JList.of(OpenMainTestModulesSettingsQuickFix.quickFix)

          override def getNavigatable(project: Project): Navigatable = null
        }
        esReporter.warning(buildIssue)
      case _ =>
    }
}

private object OpenMainTestModulesSettingsQuickFix {
  val ID = "open_main_test_modules_settings"

  val quickFix: BuildIssueQuickFix = new BuildIssueQuickFix {
    override def getId: String = ID

    override def runQuickFix(project: Project, dataContext: DataContext): CompletableFuture[?] = {
      ShowSettingsUtilImplExt.showSettingsDialog(project, classOf[SbtExternalSystemConfigurable], SbtBundle.message("separate.prod.test.modules"))
      CompletableFuture.completedFuture(null)
    }
  }
}

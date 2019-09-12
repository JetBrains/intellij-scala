package org.jetbrains.sbt.project

import javax.swing.event.HyperlinkEvent
import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.notification.{Notification, NotificationGroup, NotificationListener, NotificationType}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtStartupActivity.sbtNotificationGroup
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.plugins.scala.project.ProjectExt

class SbtStartupActivity extends StartupActivity {
  override def runActivity(project: Project): Unit = {
    showNotificationForUnlinkedSbtProject(project)
  }

  val ImportDescription = "import"

  private def showNotificationForUnlinkedSbtProject(project: Project): Unit =
    if (SbtSettings.getInstance(project).getLinkedProjectsSettings.isEmpty &&
      project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) != java.lang.Boolean.TRUE &&
      SbtProjectImportProvider.canImport(project.baseDir)
    ) {
      val message = s"""<a href="$ImportDescription">Import sbt project</a>"""

      sbtNotificationGroup
        .createNotification("sbt project detected", message, NotificationType.INFORMATION, notificationListener(project))
        .notify(project)
    }

  private def notificationListener(project: Project) = new NotificationListener.Adapter() {

    override protected def hyperlinkActivated(notification: Notification, e: HyperlinkEvent): Unit = {
      notification.expire()
      if (ImportDescription == e.getDescription) {
        val sbtProjectImportProvider = new SbtProjectImportProvider()
        val wizard = new AddModuleWizard(project, project.getBasePath, sbtProjectImportProvider)

        if (wizard.getStepCount <= 0 || wizard.showAndGet)
          ImportModuleAction.createFromWizard(project, wizard)
      }
    }
  }
}

object SbtStartupActivity {
  private lazy val sbtNotificationGroup = NotificationGroup.balloonGroup(Sbt.Name)
}
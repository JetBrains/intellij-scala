package org.jetbrains.sbt.project

import com.intellij.ide.actions.ImportModuleAction
import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.notification.{Notification, NotificationListener, NotificationType}
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.language.SbtProjectService
import org.jetbrains.sbt.settings.SbtSettings

class SbtStartupActivity extends StartupActivity {

  override def runActivity(project: Project): Unit = {
    showNotificationForUnlinkedSbtProject(project)
    SbtProjectService.getInstance(project)
  }

  val ImportDescription = "import"

  private def showNotificationForUnlinkedSbtProject(project: Project): Unit =
    if (SbtSettings.getInstance(project).getLinkedProjectsSettings.isEmpty &&
      project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) != java.lang.Boolean.TRUE &&
      SbtProjectImportProvider.canImport(project.baseDir)
    ) {
      val message = s"""<a href="$ImportDescription">Import sbt project</a>"""

      Sbt.balloonNotification
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

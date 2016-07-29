package org.jetbrains.sbt
package language

import java.util
import javax.swing.event.HyperlinkEvent

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.{Notification, NotificationDisplayType, NotificationListener}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.util.Consumer
import org.jetbrains.idea.maven.indices.{MavenIndex, MavenProjectIndicesManager}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.resolvers.SbtMavenRepositoryProvider

/**
 * @author Pavel Fatin
 */
class SbtProjectComponent(project: Project) extends AbstractProjectComponent(project) {

  private val SBT_MAVEN_NOTIFICATION_GROUP = "Unindexed maven repositories for SBT detection"

  override def initComponent() {
    manager.addPsiTreeChangeListener(TreeListener)
  }

  override def disposeComponent() {
    manager.removePsiTreeChangeListener(TreeListener)
  }

  private def manager = PsiManager.getInstance(project)

  private def analyzer = DaemonCodeAnalyzer.getInstance(project)

  object TreeListener extends PsiTreeChangeAdapter {
    override def childrenChanged(event: PsiTreeChangeEvent) {
      event.getFile match {
        case file: SbtFileImpl => analyzer.restart(file)
        case _ =>
      }
    }
  }

  private def setupMavenIndexes() = {
    MavenProjectIndicesManager.getInstance(project).scheduleUpdateIndicesList(new Consumer[util.List[MavenIndex]] {
      override def consume(mavenIndexes: util.List[MavenIndex]): Unit = {
        import scala.collection.JavaConversions._

        if (project.isDisposed) return

        val sbtRepos = (new SbtMavenRepositoryProvider).getRemoteRepositories(project).map(_.getUrl).toSet
        val unindexedRepos = mavenIndexes.filter(idx => idx.getUpdateTimestamp == -1 && sbtRepos.contains(idx.getRepositoryPathOrUrl))
        if (unindexedRepos.isEmpty) return

        val notificationData = new NotificationData(
          s"<b>${unindexedRepos.length} Unindexed maven repositories found</b>",
          s"""
            |If you want to use dependency completion, click
            |<b><a href="#open">here</a></b>, select required repositories and press "Update" button. <a href="#disable">Disable...</a>
          """.stripMargin,
          NotificationCategory.WARNING,
          NotificationSource.PROJECT_SYNC
        )
        notificationData.setBalloonNotification(true)
        notificationData.setBalloonGroup(SBT_MAVEN_NOTIFICATION_GROUP)
        notificationData.setListener("#open", new NotificationListener.Adapter {
          protected def hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
//            ShowSettingsUtil.getInstance.showSettingsDialog(project, ProjectStructureConfigurable.getInstance(project))
            val ui = ProjectStructureConfigurable.getInstance(project)
            val editor = new SingleConfigurableEditor(project, ui)
            val module = ui.getModulesConfig.getModules.find(ModuleType.get(_).isInstanceOf[SbtModuleType])
            ui.select(module.get.getName, null, false)
            editor.show()
            //            ShowSettingsUtil.getInstance.showSettingsDialog(myProject, classOf[SbtModuleSettingsEditor])
//            notification.expire()
          }
        })
        notificationData.setListener("#disable", new NotificationListener.Adapter() {
          protected def hyperlinkActivated(notification: Notification, e: HyperlinkEvent) {
            val result: Int = Messages.showYesNoDialog(myProject,
              s"""Notification will be disabled for all projects.
                 |
                 |Settings | Appearance & Behavior | Notifications | $SBT_MAVEN_NOTIFICATION_GROUP
                 |can be used to configure the notification.""".stripMargin,
              "Unindexed Maven Repositories SBT Detection",
              "Disable Notification",
              CommonBundle.getCancelButtonText,
              Messages.getWarningIcon)
            if (result == Messages.YES) {
              NotificationsConfigurationImpl.getInstanceImpl.changeSettings(SBT_MAVEN_NOTIFICATION_GROUP, NotificationDisplayType.NONE, false, false)
              notification.hideBalloon()
            }
          }
        })
        ExternalSystemNotificationManager.getInstance(project).showNotification(SbtProjectSystem.Id, notificationData)
      }
    })
  }

  override def projectOpened(): Unit = {
   setupMavenIndexes()
  }
}

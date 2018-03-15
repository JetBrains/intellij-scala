package org.jetbrains.sbt
package language

import java.util
import javax.swing.event.HyperlinkEvent

import com.intellij.CommonBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.{Notification, NotificationDisplayType}
import com.intellij.openapi.application.{ApplicationManager, TransactionGuard}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import com.intellij.util.Consumer
import org.jetbrains.idea.maven.indices.{MavenIndex, MavenProjectIndicesManager}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.module.SbtModuleType
import org.jetbrains.sbt.resolvers.indexes.IvyIndex
import org.jetbrains.sbt.resolvers.{SbtMavenRepositoryProvider, SbtResolverUtils}

import scala.collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
class SbtProjectComponent(project: Project) extends AbstractProjectComponent(project) {

  private val SBT_MAVEN_NOTIFICATION_GROUP = "Unindexed maven repositories for sbt detection"

  override def projectOpened(): Unit = {
    manager.addPsiTreeChangeListener(TreeListener)

    // disabled feature as too annoying
    setupMavenIndexes()
  }

  override def projectClosed(): Unit = {
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

  val unindexedNotifier: Consumer[util.List[MavenIndex]] = new Consumer[util.List[MavenIndex]] {
    override def consume(mavenIndexes: util.List[MavenIndex]): Unit = {

      if (project.isDisposed) return
      val sbtRepos = (new SbtMavenRepositoryProvider)
        .getRemoteRepositories(project)
        .asScala
        .map(_.getUrl)
        .toSet

      val unindexedRepos = mavenIndexes
        .asScala
        .filter(idx => idx.getUpdateTimestamp == -1 && sbtRepos.contains(idx.getRepositoryPathOrUrl))

      if (unindexedRepos.isEmpty) return
      val title = s"<b>${unindexedRepos.length} Unindexed maven repositories found</b>"
      val message =
        s"""
          |If you want to use dependency completion, click
          |<b><a href="#open">here</a></b>, select required repositories and press "Update" button. <a href="#disable">Disable...</a>
          """.stripMargin
      val notificationData = createIndexerNotification(title, message)
      ExternalSystemNotificationManager.getInstance(project).showNotification(SbtProjectSystem.Id, notificationData)
    }
  }

  private def setupMavenIndexes(): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) return

    if (isIdeaPluginEnabled("org.jetbrains.idea.maven")) {
      MavenProjectIndicesManager.getInstance(project).scheduleUpdateIndicesList(null)
    } else {
      notifyDisabledMavenPlugin()
    }
  }

  private def notifyDisabledMavenPlugin(): Unit = {
    val outdatedIvyIndexes = SbtResolverUtils.getProjectResolvers(project)
      .flatMap(_.getIndex(project))
      .filter { index =>
        index.isInstanceOf[IvyIndex] && index.getUpdateTimeStamp == -1
      }
    if (outdatedIvyIndexes.isEmpty) return
    val title = s"<b>${outdatedIvyIndexes.size} Unindexed Ivy repositories found</b>"
    val message =
      s"""
         |Update repositories <b><a href="#open">here</a></b> to use dependency completion.
         |Enable Maven plugin to use extra indexes.<a href="#disable">Disable...</a>
          """.stripMargin
    val notificationData = createIndexerNotification(title, message)
    ExternalSystemNotificationManager.getInstance(project).showNotification(SbtProjectSystem.Id, notificationData)
  }

  def createIndexerNotification(title: String, message: String): NotificationData = {
    val notificationData = new NotificationData(
      title,
      message,
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC)
    notificationData.setBalloonNotification(true)
    notificationData.setBalloonGroup(SBT_MAVEN_NOTIFICATION_GROUP)
    notificationData.setListener(
      "#open", (notification: Notification, e: HyperlinkEvent) => {
        val ui = ProjectStructureConfigurable.getInstance(project)
        val editor = new SingleConfigurableEditor(project, ui)
        val module = ui.getModulesConfig.getModules.find(ModuleType.get(_).isInstanceOf[SbtModuleType])
        ui.select(module.get.getName, "sbt", false)
        //Project Structure should be shown in a transaction
        TransactionGuard.getInstance().submitTransactionAndWait(() => editor.show())
      })
    notificationData.setListener(
      "#disable", (notification: Notification, e: HyperlinkEvent) => {
        val result: Int = Messages.showYesNoDialog(myProject,
          s"""Notification will be disabled for all projects
Settings | Appearance & Behavior | Notifications | $SBT_MAVEN_NOTIFICATION_GROUP
can be used to configure the notification.""".stripMargin,
          "Unindexed Maven Repositories sbt Detection",
          "Disable Notification",
          CommonBundle.getCancelButtonText,
          Messages.getWarningIcon)
        if (result == Messages.YES) {
          NotificationsConfigurationImpl.getInstanceImpl.changeSettings(SBT_MAVEN_NOTIFICATION_GROUP, NotificationDisplayType.NONE, false, false)
          notification.hideBalloon()
        }
      })
    notificationData
  }
}

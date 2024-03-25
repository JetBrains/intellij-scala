package org.jetbrains.plugins.scala.project.external

import com.intellij.notification.NotificationGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.nowarn

abstract class ScalaAbstractProjectDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  override def getTargetDataKey: Key[E] = key

  protected implicit class IdeModifiableModelsProviderOps(private val modelsProvider: IdeModifiableModelsProvider) {

    def findIdeModuleOpt(name: String): Option[Module] =
      Option(modelsProvider.findIdeModule(name))

    def findIdeModuleOpt(data: ModuleData): Option[Module] =
      Option(modelsProvider.findIdeModule(data))

    def getIdeModuleByNode(node: DataNode[_]): Option[Module] = {
      val key = Option(node.getParent(classOf[ModuleData]))
        .map(_.getKey)
        .getOrElse(ProjectKeys.MODULE)
      for {
        moduleData <- Option(node.getData(key))
        module <- findIdeModuleOpt(moduleData)
      } yield module
    }
  }

  @nowarn("cat=deprecation")
  protected final def executeProjectChangeAction(project: Project)(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  protected final def executeProjectChangeAction(action: => Unit)(implicit project: ProjectContext): Unit =
    executeProjectChangeAction(project.project)(action)

  protected def showScalaLibraryNotFoundWarning(
    title: NlsString,
    version: String,
    module: String,
    balloonGroup: NotificationGroup,
    systemId: ProjectSystemId,
  )(implicit project: Project): Unit = {
    showWarning(
      title,
      NlsString(ScalaBundle.message("scala.project.data.service.scalaLibraryNotFound", module, version)),
      balloonGroup,
      systemId
    )
  }

  protected final def showWarning(
    title: NlsString,
    message: NlsString,
    balloonGroup: NotificationGroup,
    systemId: ProjectSystemId
  )(implicit project: Project): Unit = {
    val notificationData = new NotificationData(
      title.nls,
      message.nls,
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    )
    notificationData.setBalloonGroup(balloonGroup)

    // TODO: maybe show notification in Build (where all the other importing progress is shown) and not in the "Messages" tool window
    ExternalSystemNotificationManager.getInstance(project).showNotification(systemId, notificationData)

    if (ApplicationManager.getApplication.isUnitTestMode) {
      val notificationBefore = Option(project.getUserData(ShownNotificationsKey)).getOrElse(Nil)
      val notificationsNew = notificationBefore :+ ShownNotification(systemId, notificationData)
      project.putUserData(ShownNotificationsKey, notificationsNew)
    }
  }
}

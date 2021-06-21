package org.jetbrains.plugins.scala
package project
package external

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, Key, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationCategory, NotificationData, NotificationSource}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService.NotificationException
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.SbtProjectSystem

abstract class ScalaAbstractProjectDataService[E, I](key: Key[E]) extends AbstractProjectDataService[E, I] {

  override def getTargetDataKey: Key[E] = key

  protected implicit class IdeModifiableModelsProviderOps(private val modelsProvider: IdeModifiableModelsProvider) {

    def findIdeModuleOpt(name: String): Option[Module] =
      Option(modelsProvider.findIdeModule(name))

    def findIdeModuleOpt(data: ModuleData): Option[Module] =
      Option(modelsProvider.findIdeModule(data))

    def getIdeModuleByNode(node: DataNode[_]): Option[Module] =
      for {
        moduleData <- Option(node.getData(ProjectKeys.MODULE))
        module <- findIdeModuleOpt(moduleData)
      } yield module
  }

  protected final def executeProjectChangeAction(project: Project)(action: => Unit): Unit =
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      override def execute(): Unit = action
    })

  protected final def executeProjectChangeAction(action: => Unit)(implicit project: ProjectContext): Unit =
    executeProjectChangeAction(project.project)(action)

  protected def showScalaLibraryNotFoundWarning(
    title: NlsString,
    balloonGroup: String,
    version: String,
    module: String
  )(implicit project: Project): Unit = {
    showWarning(
      title,
      NlsString(ScalaBundle.message("scala.project.data.service.scalaLibraryNotFound", module, version)),
      balloonGroup
    )
  }

  protected final def showWarning(title: NlsString, message: NlsString, balloonGroup: String)
                                 (implicit project: Project): Unit = {
    val notificationData = new NotificationData(
      title.nls,
      message.nls,
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    )
    notificationData.setBalloonGroup(balloonGroup)

    val systemId = SbtProjectSystem.Id
    if (ApplicationManager.getApplication.isUnitTestMode)
      throw NotificationException(notificationData, systemId)
    else {
      // TODO: maybe show notification in Build (where all the other importing progress is shown) and not in the "Messages" tool window
      ExternalSystemNotificationManager.getInstance(project).showNotification(systemId, notificationData)
    }
  }
}

object ScalaAbstractProjectDataService {

  case class NotificationException(notificationData: NotificationData, id: ProjectSystemId) extends RuntimeException(
    s"""Notification was shown during $id module creation.
       |Category: ${notificationData.getNotificationCategory}
       |Title: ${notificationData.getTitle}
       |Message: ${notificationData.getMessage}
       |NotificationSource: ${notificationData.getNotificationSource}
       |""".stripMargin
  )
}


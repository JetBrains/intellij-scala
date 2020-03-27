package org.jetbrains.plugins.scala.components

import java.util

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.notification._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.NotificationUtil.HyperlinkListener
import org.jetbrains.sbt.project.settings.{SbtProjectSettings, SbtProjectSettingsListener, SbtTopic}

class Scala3Disclaimer extends ApplicationInitializedListener {
  override def componentsInitialized(): Unit = {
    val applicationBusConnection = ApplicationManager.getApplication.getMessageBus.connect()
    applicationBusConnection.subscribe(ProjectManager.TOPIC, projectListener)
  }

  private def projectListener = new ProjectManagerListener {
    override def projectOpened(project: Project): Unit = {
      onProjectLoaded(project) // for IDEA-based projects

      val projectBusConnection = project.getMessageBus.connect()

      projectBusConnection.subscribe(SbtTopic, new SbtProjectSettingsListener {
        override def onProjectsLoaded(settings: util.Collection[SbtProjectSettings]): Unit = {
          onProjectLoaded(project) // for non-IDEA-based projects (it's Ok to call the method more than once)
        }
        override def onProjectRenamed(oldName: String, newName: String): Unit = {}
        override def onProjectsLinked(settings: util.Collection[SbtProjectSettings]): Unit = {}
        override def onProjectsUnlinked(linkedProjectPaths: util.Set[String]): Unit = {}
        override def onBulkChangeStart(): Unit = {}
        override def onBulkChangeEnd(): Unit = {}
      })
    }
  }

  private def onProjectLoaded(project: Project): Unit = {
    if (!isShownIn(project)) {
      if (project.hasScala3) {
        showDisclaimer()
        setShownIn(project)
      }
    }
  }

  private def isShownIn(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isScala3DisclaimerShown

  private def setShownIn(project: Project): Unit = {
    ScalaProjectSettings.getInstance(project).setScala3DisclaimerShown(true)
  }

  private def showDisclaimer(): Unit = {
    val message =
      "Scala 3 support is work in progress.<br>" +
        "Consider using nightly builds. <a href='https://blog.jetbrains.com/scala/2020/03/17/scala-3-support-in-intellij-scala-plugin/'>Learn more</a>"

    val notification = {
      val group = new NotificationGroup("Scala 3 disclaimer", NotificationDisplayType.STICKY_BALLOON, false)
      group.createNotification(message, NotificationType.INFORMATION)
    }

    notification.setListener(new HyperlinkListener())

    Notifications.Bus.notify(notification)
  }
}

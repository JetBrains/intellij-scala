package org.jetbrains.plugins.scala.components

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.ScalaNotificationGroups
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

import java.util.concurrent.ExecutorService

private object Scala38UpdateIdeDisclaimer {

  private final val Shown: Key[Boolean] = Key.create("scala3.8.update.ide.disclaimer.shown")

  private val executor: ExecutorService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Scala 3.8 update ide disclaimer executor", 1)

  private def isShownIn(project: Project): Boolean = Shown.get(project, false)

  private def setShownIn(project: Project): Unit = {
    Shown.set(project, true)
  }

  def canBeShownIn(project: Project): Boolean =
    highestScalaVersion(project).exists(isScala38orLater)

  private def show(project: Project, scalaVersion: ScalaVersion): Unit = {
    ScalaNotificationGroups.scala38UpdateIdeDisclaimer
      .createNotification(
        ScalaBundle.message("scala.3.8.plus.support.title"),
        ScalaBundle.message("scala.3.8.support.update.ide.disclaimer.text", scalaVersion.minor),
        NotificationType.WARNING
      )
      .setImportant(true)
      .setSuggestionType(true)
      .setImportantSuggestion(true)
      .notify(project)
    setShownIn(project)
  }

  final class ProjectActivity extends org.jetbrains.plugins.scala.startup.ProjectActivity {
    override def execute(project: Project): Unit = {
      if (isUnitTestMode) return
      executor.execute { () =>
        showDisclaimer(project)
      }
    }
  }

  final class DumbModeListener extends com.intellij.openapi.project.DumbService.DumbModeListener {
    override def exitDumbMode(): Unit = {
      if (isUnitTestMode) return
      executor.execute { () =>
        ProjectManager.getInstance().getOpenProjects.foreach(showDisclaimer)
      }
    }
  }

  private def showDisclaimer(project: Project): Unit = {
    if (project.isDisposed || project.isDefault) return

    if (!isShownIn(project)) {
      highestScalaVersion(project) match {
        case Some(version) if isScala38orLater(version) => show(project, version)
        case _ =>
      }
    }
  }

  def highestScalaVersion(project: Project): Option[ScalaVersion] =
    project.allScalaVersions.maxOption

  def isScala38orLater(scalaVersion: ScalaVersion): Boolean =
    scalaVersion.languageLevel >= ScalaLanguageLevel.Scala_3_8

  private def isUnitTestMode: Boolean =
    ApplicationManager.getApplication.isUnitTestMode
}

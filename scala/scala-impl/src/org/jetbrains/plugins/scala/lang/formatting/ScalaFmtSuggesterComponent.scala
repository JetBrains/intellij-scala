package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.notification.{Notification, NotificationAction, NotificationGroup}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.{AbstractProjectComponent, PersistentStateComponent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.components._

import scala.beans.BeanProperty

@State(name = "SuggestScalaFmt", storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE)))
class ScalaFmtSuggesterComponent(val project: Project) extends ProjectComponent with PersistentStateComponent[ScalaFmtSuggesterComponent.State] {

  import ScalaFmtSuggesterComponent._

  override def projectOpened(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    if (!settings.USE_SCALAFMT_FORMATTER && ScalaFmtPreFormatProcessor.projectDefaultConfig(project).nonEmpty && state.enableForCurrentProject) {
      //suggest the feature automatically
      settings.DETECT_SCALAFMT match {
        case ScalaCodeStyleSettings.ASK_SCALAFMT_ENABLE => createNotification.notify(project)
        case ScalaCodeStyleSettings.ALWAYS_SCALAFMT_ENABLE => enableForProject(project)
        case _ =>
      }
    }
  }

  private var state: ScalaFmtSuggesterComponent.State = new ScalaFmtSuggesterComponent.State()

  override def getState: ScalaFmtSuggesterComponent.State = {
    state
  }

  override def loadState(state: ScalaFmtSuggesterComponent.State): Unit = {
    this.state = state
  }

  private def createNotification: Notification =
    notificationGroup.createNotification("Scalafmt configuration detected. Enable scalafmt formatting for current project?", MessageType.INFO)
      .addAction(enableForProjectAction(project)).addAction(enableAlwaysAction(project)).addAction(disableForProjectAction)

  private val disableForProjectAction: NotificationAction = {
    new NotificationAction("Don't show for current project") {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        val newState = new ScalaFmtSuggesterComponent.State()
        newState.enableForCurrentProject = false
        loadState(newState)
        notification.expire()
      }
    }
  }
}

object ScalaFmtSuggesterComponent {

  class State {
    @BeanProperty
    var enableForCurrentProject: Boolean = true
  }

  val notificationGroup: NotificationGroup = NotificationGroup.balloonGroup("Scalafmt suggester")

  private def enableForProject(project: Project): Unit = {
    val codeStyleSchemesModel = new CodeStyleSchemesModel(project)
    var scheme = codeStyleSchemesModel.getSelectedScheme
    if (!codeStyleSchemesModel.isProjectScheme(scheme)) {
      codeStyleSchemesModel.copyToProject(scheme)
      scheme = codeStyleSchemesModel.getProjectScheme
    }
    val newSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    newSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    codeStyleSchemesModel.apply()
  }

  private def enableForProjectAction(project: Project): NotificationAction = {
    new NotificationAction("Enable for current project") {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        enableForProject(project)
        notification.expire()
      }
    }
  }

  private def enableAlwaysAction(project: Project): NotificationAction = {
    new NotificationAction("Enable where applicable") {
      override def actionPerformed(e: AnActionEvent, notification: Notification): Unit = {
        enableForProject(project)
        ScalaCodeStyleSettings.getInstance(project).DETECT_SCALAFMT = ScalaCodeStyleSettings.ALWAYS_SCALAFMT_ENABLE
        notification.expire()
      }
    }
  }
}
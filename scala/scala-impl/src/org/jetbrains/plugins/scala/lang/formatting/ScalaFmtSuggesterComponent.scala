package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.notification._
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.components._
import javax.swing.event.HyperlinkEvent

import scala.beans.BeanProperty

@State(name = "SuggestScalaFmt", storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE)))
class ScalaFmtSuggesterComponent(val project: Project) extends ProjectComponent with PersistentStateComponent[ScalaFmtSuggesterComponent.State] {

  import ScalaFmtSuggesterComponent._

  override def projectOpened(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    if (!settings.USE_SCALAFMT_FORMATTER && ScalaFmtPreFormatProcessor.projectDefaultConfig(project).nonEmpty && state.enableForCurrentProject) {
      //suggest the feature automatically
      settings.DETECT_SCALAFMT match {
        case ScalaCodeStyleSettings.ASK_SCALAFMT_ENABLE =>
          createNotification.notify(project)
        case ScalaCodeStyleSettings.ALWAYS_SCALAFMT_ENABLE =>
          enableForProject()
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

  private def enableForProject(): Unit = {
    val codeStyleSchemesModel = new CodeStyleSchemesModel(project)
    var scheme = codeStyleSchemesModel.getSelectedScheme
    if (!codeStyleSchemesModel.isProjectScheme(scheme)) {
      codeStyleSchemesModel.copyToProject(scheme)
      scheme = codeStyleSchemesModel.getProjectScheme
    }
    val newSettings = scheme.getCodeStyleSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    newSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
    codeStyleSchemesModel.apply()
    notificationGroup.createNotification("Scalafmt formatting enabled for current project", NotificationType.INFORMATION).notify(project)
  }

  private def createNotification: Notification = {
    val builder = new StringBuilder()
    builder.append("Enable scalafmt formatting for current project?<br/>").append(wrapInRef(enableProjectDescription, enableProjectText))
    if (!isProjectLevelConfiguration) builder.append(wrapInRef(enableAllDescription, enableAllText))
    builder.append(wrapInRef(dontShowDescription, dontShowText))
    notificationGroup.createNotification("Scalafmt configuration detected", builder.toString(), NotificationType.INFORMATION, listener)
  }

  private val listener: NotificationListener = new NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, e: HyperlinkEvent): Unit = {
      notification.expire()
      e.getDescription match {
        case `enableProjectDescription` =>
          enableForProject()
        case `enableAllDescription` =>
          val settings = ScalaCodeStyleSettings.getInstance(project)
          settings.DETECT_SCALAFMT = ScalaCodeStyleSettings.ALWAYS_SCALAFMT_ENABLE
          settings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
        case `dontShowDescription` =>
          val newState = new ScalaFmtSuggesterComponent.State()
          newState.enableForCurrentProject = false
          loadState(newState)
        case _ =>
      }
    }
  }

  private def wrapInRef(description: String, text: String) = s"""<a href="$description">$text</a><br/>"""
  private val enableProjectDescription = "enable"
  private val enableProjectText = "Enable for current project"
  private val enableAllDescription = "enableAll"
  private val enableAllText = "Enable for all projects with scalafmt configurations"
  private val dontShowDescription = "dont show"
  private val dontShowText = "Don't show for current project"

  private def isProjectLevelConfiguration: Boolean = {
    val schemesModel = new CodeStyleSchemesModel(project)
    schemesModel.isUsePerProjectSettings
  }
}

object ScalaFmtSuggesterComponent {

  class State {
    @BeanProperty
    var enableForCurrentProject: Boolean = true
  }

  val notificationGroup: NotificationGroup = NotificationGroup.balloonGroup("Scalafmt suggester")
}
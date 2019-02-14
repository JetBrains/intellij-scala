package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.notification._
import com.intellij.openapi.components.{PersistentStateComponent, _}
import com.intellij.openapi.project.Project
import javax.swing.event.HyperlinkEvent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

import scala.beans.BeanProperty

@State(name = "SuggestScalaFmt", storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE)))
class ScalaFmtSuggesterComponent(val project: Project) extends ProjectComponent with PersistentStateComponent[ScalaFmtSuggesterComponent.State] {

  import ScalaFmtSuggesterComponent._

  override def projectOpened(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    if (!settings.USE_SCALAFMT_FORMATTER &&
      projectHasScalafmtDefaultConfigFile &&
      state.enableForCurrentProject
    ) {
      //suggest the feature automatically
      createNotification.notify(project)
    }
  }

  private def projectHasScalafmtDefaultConfigFile: Boolean = {
    project.getBaseDir.toOption
      .flatMap(_.findChild(ScalafmtDynamicConfigUtil.DefaultConfigurationFileName).toOption)
      .nonEmpty
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
    newSettings.SCALAFMT_CONFIG_PATH = ""
    codeStyleSchemesModel.apply()
  }

  private def createNotification: Notification = {
    suggesterNotificationGroup.createNotification("Scalafmt configuration detected in this project",
      wrapInRef(enableProjectDescription, enableProjectText) + wrapInRef(dontShowDescription, dontShowText),
      NotificationType.INFORMATION, listener)
  }

  private val listener: NotificationListener = new NotificationListener.Adapter {
    override def hyperlinkActivated(notification: Notification, e: HyperlinkEvent): Unit = {
      notification.expire()
      e.getDescription match {
        case `enableProjectDescription` =>
          enableForProject()
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
  private val enableProjectText = "Use scalafmt formatter"
  private val dontShowDescription = "dont show"
  private val dontShowText = "Continue using IntelliJ formatter"

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

  val suggesterNotificationGroup: NotificationGroup = NotificationGroup.balloonGroup("Scalafmt detection")
}
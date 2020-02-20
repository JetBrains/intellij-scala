package org.jetbrains.plugins.scala.lang.formatting.scalafmt

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.notification._
import com.intellij.openapi.components.{PersistentStateComponent, _}
import com.intellij.openapi.project.Project
import javax.swing.event.HyperlinkEvent
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.beans.BeanProperty

@State(
  name = "SuggestScalaFmt",
  storages = Array[Storage](new Storage(value = StoragePathMacros.WORKSPACE_FILE))
)
final class ScalaFmtSuggesterService(private val project: Project)
  extends PersistentStateComponent[ScalaFmtSuggesterService.State] {

  import ScalaFmtSuggesterService._

  def init(): Unit =
    if (needToSuggestScalafmtFormatter(project)) {
      createNotification.notify(project)
    }

  private def needToSuggestScalafmtFormatter(project: Project): Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(project)
    !settings.USE_SCALAFMT_FORMATTER &&
      projectHasScalafmtDefaultConfigFile &&
      state.enableForCurrentProject
  }

  private def projectHasScalafmtDefaultConfigFile: Boolean = {
    project.baseDir.toOption
      .flatMap(_.findChild(ScalafmtDynamicConfigService.DefaultConfigurationFileName).toOption)
      .nonEmpty
  }

  private var state: ScalaFmtSuggesterService.State = new ScalaFmtSuggesterService.State()
  override def getState: ScalaFmtSuggesterService.State = state
  override def loadState(state: ScalaFmtSuggesterService.State): Unit = this.state = state

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

    ScalafmtDynamicConfigService.instanceIn(project).init()
  }

  private def dontShow(): Unit = {
    val newState = new scalafmt.ScalaFmtSuggesterService.State()
    newState.enableForCurrentProject = false
    loadState(newState)
  }

  private def createNotification: Notification =
    suggesterNotificationGroup.createNotification(
      ScalaBundle.message("scalafmt.suggester.detected.in.project"),
      wrapInRef(EnableRef, enableProjectText) + Br +
        wrapInRef(DontShowRef, dontShowText) + Br,
      NotificationType.INFORMATION, listener
    )

  private val listener: NotificationListener = (notification: Notification, link: HyperlinkEvent) => {
    notification.expire()
    link.getDescription match {
      case EnableRef   => enableForProject()
      case DontShowRef => dontShow()
      case _           =>
    }
  }

  //noinspection HardCodedStringLiteral
  private def wrapInRef(ref: String, text: String): String = s"""<a href="$ref">$text</a>"""

  @NonNls private val Br = "<br/>"

  @NonNls private val EnableRef   = "bytecode.indices.enable"
  @NonNls private val DontShowRef = "dont show"

  private val enableProjectText = ScalaBundle.message("scalafmt.suggester.use.scalafmt.formatter")
  private val dontShowText      = ScalaBundle.message("scalafmt.suggester.continue.using.intellij.formatter")
}

object ScalaFmtSuggesterService {

  def instance(implicit project: Project): ScalaFmtSuggesterService =
    project.getService(classOf[ScalaFmtSuggesterService])

  class State {
    @BeanProperty
    var enableForCurrentProject: Boolean = true
  }

  private val suggesterNotificationGroup: NotificationGroup =
    NotificationGroup.balloonGroup("Scalafmt detection")
}
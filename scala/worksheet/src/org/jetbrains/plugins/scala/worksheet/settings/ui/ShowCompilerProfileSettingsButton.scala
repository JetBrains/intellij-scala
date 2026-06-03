package org.jetbrains.plugins.scala.worksheet.settings.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfigurable, ScalaCompilerProfilesPanel}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

private final class ShowCompilerProfileSettingsButton(
  selectedProfileProvider: () => String,
  profilesUpdatedListener: () => Unit
) extends AnAction(
  WorksheetBundle.message("worksheet.show.compiler.profiles.settings"),
  null,
  AllIcons.General.Settings
) {

  override def actionPerformed(anActionEvent: AnActionEvent): Unit = {
    val project = anActionEvent.getProject
    val selectedProfile = Option(selectedProfileProvider())
    if (showScalaCompilerSettingsDialog(project, selectedProfile)) {
      profilesUpdatedListener()
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  def getActionButton: ActionButton =
    new ActionButton(this, null, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

  private def showScalaCompilerSettingsDialog(project: Project, selectedProfile: Option[String]): Boolean =
    ScalaCompilerProfilesPanel.withTemporarySelectedProfile(project, selectedProfile) {
      val dialogOpt: Option[DialogWrapper] = {
        val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
        val configurableCandidates = SearchUtil.expand(groups).asScala
        val compilerConf = configurableCandidates.find {
          case config: ConfigurableWithId => config.getId == ScalaCompilerConfigurable.Id
          case _ => false
        }
        compilerConf.map { conf =>
          ShowSettingsUtilImpl.getDialog(project, groups.toList.asJava, conf): @nowarn("cat=deprecation") // IJPL-715
        }
      }
      dialogOpt match {
        case Some(dialog) => dialog.showAndGet()
        case None         => false
      }
    }
}

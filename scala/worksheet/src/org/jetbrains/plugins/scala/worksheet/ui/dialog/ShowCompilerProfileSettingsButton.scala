package org.jetbrains.plugins.scala.worksheet.ui.dialog

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, AnAction, AnActionEvent}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfigurable
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerProfilesPanel

import scala.jdk.CollectionConverters._

class ShowCompilerProfileSettingsButton(form: WorksheetSettingsSetForm)
  extends AnAction(ScalaBundle.message("worksheet.show.compiler.profiles.settings"), null, AllIcons.General.Settings) {

  override def actionPerformed(anActionEvent: AnActionEvent): Unit = {
    val project = anActionEvent.getProject
    val selectedProfile = form.getSelectedProfileName
    if (showScalaCompilerSettingsDialog(project, selectedProfile)) {
      profilesReload()
    }
  }

  def getActionButton: ActionButton =
    new ActionButton(this, getTemplatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)

  private def showScalaCompilerSettingsDialog(project: Project, selectedProfile: String): Boolean =
    ScalaCompilerProfilesPanel.withTemporarySelectedProfile(project, selectedProfile) {
      val dialog: Option[DialogWrapper] = {
        val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
        val compilerConf = SearchUtil.expand(groups).asScala.find(_.getDisplayName == ScalaCompilerConfigurable.Name)
        compilerConf.map { conf => ShowSettingsUtilImpl.getDialog(project, groups.toList.asJava, conf) }
      }
      dialog match {
        case Some(value) => value.showAndGet()
        case None        => false
      }
    }

  private def profilesReload(): Unit = {
    val (selected, profiles) = WorksheetFileSettingsDialog.createCompilerProfileOptions(WorksheetFileSettings(form.getFile))
    form.onProfilesReload(selected, profiles)
  }
}

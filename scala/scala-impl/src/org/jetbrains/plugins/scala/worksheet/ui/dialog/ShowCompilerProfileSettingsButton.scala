package org.jetbrains.plugins.scala.worksheet.ui.dialog

import java.lang

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, AnAction, AnActionEvent}
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings

import scala.collection.JavaConverters._

/**
  * User: Dmitry.Naydanov
  * Date: 20.02.18.
  */
class ShowCompilerProfileSettingsButton(form: WorksheetFileSettingsForm) extends AnAction("", "Show compiler profiles settings", AllIcons.General.Settings) {
  override def actionPerformed(anActionEvent: AnActionEvent): Unit = {
    getDialogForCallback(anActionEvent.getProject).foreach {
      dialog => 
        if (dialog.showAndGet()) profilesReload()
    }
  }
  
  def getActionButton: ActionButton = 
    new ActionButton(this, getTemplatePresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  
  private def getDialogForCallback(project: Project): Option[DialogWrapper] = {
    val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
    
    SearchUtil.expand(groups).asScala.find {
      conf => conf.getDisplayName == "Scala Compiler"
    } map {
      compilerConf => 
        ShowSettingsUtilImpl.getDialog(project, groups, compilerConf)
    }
  }
  
  private def profilesReload() {
    val (selected, profiles) = WorksheetFileSettingsDialog.createCompilerProfileOptions(WorksheetCommonSettings.getInstance(form.getFile))
    form.onProfilesReload(selected, profiles)
  }
}

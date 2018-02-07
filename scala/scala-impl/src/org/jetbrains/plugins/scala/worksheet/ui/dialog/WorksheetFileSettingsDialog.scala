package org.jetbrains.plugins.scala.worksheet.ui.dialog

import javax.swing.{JComponent, SwingConstants}

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile

/**
  * User: Dmitry.Naydanov
  * Date: 05.02.18.
  */
class WorksheetFileSettingsDialog(worksheetFile: PsiFile) extends DialogWrapper(worksheetFile.getProject, true, true) {
  private val myPanel = new WorksheetFileSettingsForm(worksheetFile)
  setTitle(s"Settings for ${worksheetFile.getName}")
  setButtonsAlignment(SwingConstants.CENTER)
  init()
  
  override def createCenterPanel(): JComponent = myPanel.getMainPanel

  override def doOKAction(): Unit = {
    myPanel.applyTo()
    super.doOKAction()
  }
}

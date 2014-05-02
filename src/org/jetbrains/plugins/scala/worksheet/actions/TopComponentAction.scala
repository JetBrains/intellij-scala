package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.impl.ActionButton
import javax.swing.{JPanel, Icon}
import com.intellij.openapi.keymap.{KeymapUtil, KeymapManager}
import com.intellij.openapi.application.{ModalityState, ApplicationManager}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.project.Project

/**
 * User: Dmitry Naydanov
 * Date: 2/17/14
 */
trait TopComponentAction {
  this: AnAction =>
  
  def shortcutId: Option[String] = None
  
  def genericText = ScalaBundle message bundleKey
  
  def bundleKey: String 
  
  def actionIcon: Icon
  
  def getActionButton = {
    val button = new ActionButton(this, getTemplatePresentation, ActionPlaces.EDITOR_TOOLBAR,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    button setToolTipText genericText
    button
  }
  
  def init(panel: JPanel) {
    val presentation = getTemplatePresentation
    
    val text = shortcutId flatMap {
      case id =>
        KeymapManager.getInstance.getActiveKeymap.getShortcuts(id).headOption map {
          case shortcut => 
            genericText + (" (" + KeymapUtil.getShortcutText(shortcut) + ")")  
        }
    } getOrElse genericText

    val actionButton = getActionButton

    presentation setText text
    presentation setIcon actionIcon
    presentation setEnabled true

    ApplicationManager.getApplication.invokeAndWait(new Runnable {
      override def run() {
        panel.add(actionButton, 0)
      }
    }, ModalityState.any())
  }

  protected def updateInner(presentation: Presentation, project: Project) {
    if (project == null) return

    def enable() {
      presentation setEnabled true
      presentation setVisible true
    }

    def disable() {
      presentation setEnabled false
      presentation setVisible false
    }

    try {
      val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
      val psiFile = extensions.inReadAction(PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument))

      psiFile match {
        case sf: ScalaFile if sf.isWorksheetFile => enable()
        case _ => disable()
      }
    } catch {
      case e: Exception => disable()
    }
  }
}

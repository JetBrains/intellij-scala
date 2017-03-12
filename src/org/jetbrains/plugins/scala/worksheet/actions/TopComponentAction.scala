package org.jetbrains.plugins.scala
package worksheet.actions

import javax.swing.{Icon, JPanel}

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.{ApplicationManager, ModalityState}
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiConstructor

/**
 * User: Dmitry Naydanov
 * Date: 2/17/14
 */
trait TopComponentAction extends TopComponentDisplayable with WorksheetAction {
  this: AnAction =>
  
  def shortcutId: Option[String] = None
  
  def genericText: String = ScalaBundle message bundleKey
  
  def bundleKey: String 
  
  def actionIcon: Icon
  
  def getActionButton: ActionButton = {
    val button = new ActionButton(this, getTemplatePresentation, ActionPlaces.EDITOR_TOOLBAR,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
    button setToolTipText genericText
    button
  }
  
  override def init(panel: JPanel) {
    val presentation = getTemplatePresentation

    presentation setIcon actionIcon

    val text = shortcutId flatMap {
      id =>
        KeymapManager.getInstance.getActiveKeymap.getShortcuts(id).headOption map (shortcut =>
          genericText + (" (" + KeymapUtil.getShortcutText(shortcut) + ")"))
    } getOrElse genericText

    presentation setText text

    val actionButton = getActionButton
    WorksheetUiConstructor.fixUnboundMaxSize(actionButton)

    ApplicationManager.getApplication.invokeAndWait(new Runnable {
      override def run() {
        panel.add(actionButton, 0)
        actionButton.setEnabled(true)
        presentation setEnabled true
      }
    }, ModalityState.any())
  }

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setIcon(actionIcon)
    updateInner(e)
  }
}

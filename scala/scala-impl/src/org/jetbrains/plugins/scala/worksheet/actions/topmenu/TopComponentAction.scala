package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, AnAction, AnActionEvent}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import javax.swing.{Icon, JPanel}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetAction
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiConstructor

trait TopComponentAction extends TopComponentDisplayable with WorksheetAction {
  this: AnAction =>

  def genericText: String

  def actionIcon: Icon

  def shortcutId: Option[String] = None

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setIcon(actionIcon)
    updateInner(e)
  }

  override def init(panel: JPanel): Unit = {
    val presentation = getTemplatePresentation

    presentation.setIcon(actionIcon)
    presentation.setText(displayText)

    val actionButton = createActionButton
    WorksheetUiConstructor.fixUnboundMaxSize(actionButton)

    invokeAndWait(ModalityState.any()) {
      panel.add(actionButton, 0)
      actionButton.setEnabled(true)
      presentation setEnabled true
    }
  }

  private def createActionButton: ActionButton = {
    val button = new ActionButton(
      this, getTemplatePresentation, ActionPlaces.EDITOR_TOOLBAR,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    )
    button.setToolTipText(genericText)
    button
  }

  private def displayText: String = {
    val shortcutHintText = for {
      id <- shortcutId
      shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts(id)
      shortcut <- shortcuts.headOption
    } yield s" (${KeymapUtil.getShortcutText(shortcut)})"

    genericText + shortcutHintText.mkString
  }
}

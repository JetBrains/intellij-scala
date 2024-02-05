package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.{ActionPlaces, ActionToolbar, ActionUpdateThread, AnAction, AnActionEvent, Presentation}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetAction
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetUiUtils

import javax.swing.{Icon, JPanel}

trait TopComponentAction extends TopComponentDisplayable with WorksheetAction {
  this: AnAction =>

  private lazy val actionButton: ActionButton = createActionButton

  @Nls
  def genericText: String

  def actionIcon: Icon

  def shortcutId: Option[String] = None

  override def update(e: AnActionEvent): Unit = {
    updatePresentationEnabled(e)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def setEnabled(flag: Boolean): Unit = actionButton.setEnabled(flag)

  override def setVisible(flag: Boolean): Unit = actionButton.setVisible(flag)

  override def init(panel: JPanel): Unit = {
    val presentation = getTemplatePresentation

    presentation.setIcon(actionIcon)
    presentation.setText(displayText)
    WorksheetUiUtils.fixUnboundMaxSize(actionButton)

    invokeAndWait(ModalityState.any()) {
      panel.add(actionButton)
      actionButton.setEnabled(true)
    }
  }

  private def createActionButton: ActionButton = {
    val presentation = new Presentation
    presentation.copyFrom(getTemplatePresentation)
    val button = new ActionButton(
      this,
      presentation,
      ActionPlaces.EDITOR_TOOLBAR,
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

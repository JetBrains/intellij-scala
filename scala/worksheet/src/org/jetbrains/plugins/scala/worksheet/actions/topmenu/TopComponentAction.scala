package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetAction

import javax.swing.Icon

trait TopComponentAction extends WorksheetAction {
  this: AnAction =>

  @Nls
  def genericText: String

  def actionIcon: Icon

  def shortcutId: Option[String] = None

  override def update(e: AnActionEvent): Unit = {
    updatePresentationEnabled(e)
    updateIconAndText(e)
  }

  protected def updateIconAndText(e: AnActionEvent): Unit = {
    e.getPresentation.setIcon(actionIcon)
    e.getPresentation.setText(displayText)
  }

  // These actions rely on PSI file and module search that must be executed on BGT
  override final def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def displayText: String = {
    val shortcutHintText = for {
      id <- shortcutId
      shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts(id)
      shortcut <- shortcuts.headOption
    } yield s" (${KeymapUtil.getShortcutText(shortcut)})"

    genericText + shortcutHintText.mkString
  }
}

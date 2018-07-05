package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.keymap.KeymapManager
import javax.swing.KeyStroke
import org.jetbrains.plugins.scala.codeInsight.implicits.ShowImplicitHintsAction.{DisableShortcuts, EnableShortcuts, setShortcuts}

class ShowImplicitHintsAction extends ToggleAction {
  setShortcuts(EnableShortcuts)

  override def isSelected(event: AnActionEvent): Boolean =
    ImplicitHints.enabled

  override def setSelected(event: AnActionEvent, value: Boolean): Unit = {
    ImplicitHints.enabled = value
    // IDEA doesn't support dedicated shortcuts to enable / disable ToggleAction
    setShortcuts(if (value) DisableShortcuts else EnableShortcuts)
  }
}

private object ShowImplicitHintsAction {
  private val Id = "Scala.ShowImplicits"

  private val EnableShortcuts = Seq(
    new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift EQUALS"), null),
    new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift ADD"), null))

  private val DisableShortcuts = Seq(
    new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift MINUS"), null),
    new KeyboardShortcut(KeyStroke.getKeyStroke("control alt shift SUBTRACT"), null))

  private def setShortcuts(shortcuts: Seq[Shortcut]): Unit = {
    val keymap = KeymapManager.getInstance().getActiveKeymap
    keymap.removeAllActionShortcuts(Id)
    shortcuts.foreach(keymap.addShortcut(Id, _))
  }
}

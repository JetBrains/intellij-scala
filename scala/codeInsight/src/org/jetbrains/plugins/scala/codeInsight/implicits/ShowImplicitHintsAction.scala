package org.jetbrains.plugins.scala.codeInsight.implicits

import com.intellij.openapi.actionSystem._
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.components.RunOnceStartupActivity

import javax.swing.KeyStroke

class ShowImplicitHintsAction extends ToggleAction(
  ScalaCodeInsightBundle.message("show.implicit.hints.action.text"),
  ScalaCodeInsightBundle.message("show.implicit.hints.action.description"),
  /* icon = */ null
) {

  override def isSelected(event: AnActionEvent): Boolean = ImplicitHints.enabled

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    ImplicitHints.enabled = state
    ImplicitHints.updateInAllEditors()
    if (!state) {
      MouseHandler.removeEscKeyListeners()
    }
  }
}

private object ShowImplicitHintsAction {
  val Id = "Scala.ShowImplicits"

  class ConfigureShortcuts extends RunOnceStartupActivity {
    override protected def doRunActivity(): Unit = {
      if (SystemInfo.isLinux) { // Workaround for SCL-21346
        val keymap = KeymapManager.getInstance.getActiveKeymap
        keymap.removeShortcut("ZoomInIdeAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt EQUALS"), null))
        keymap.removeShortcut("ZoomOutIdeAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt MINUS"), null))
        keymap.removeShortcut("ResetIdeScaleAction", new KeyboardShortcut(KeyStroke.getKeyStroke("shift control alt 0"), null))
      }

      ImplicitShortcuts.setShortcuts(ShowImplicitHintsAction.Id, ImplicitShortcuts.EnableShortcuts)
    }

    override protected def doCleanup(): Unit = {}
  }
}

package org.jetbrains.plugins.scala.annotator.hints

import java.awt.event.MouseEvent

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.JBUI
import javax.swing.JLabel
import org.jetbrains.plugins.scala.ScalaBundle

private class HintUI(override val message: String,
                     hint: LightweightHint)
  extends TooltipUI {

  override protected def showImpl(inlay: Inlay[_], e: MouseEvent): Unit = {
    val constraint = HintManager.ABOVE
    val editor = inlay.getEditor

    val point = {
      val p = HintManagerImpl.getHintPosition(hint, editor,
        editor.xyToVisualPosition(e.getPoint), constraint)
      p.x = e.getXOnScreen - editor.getContentComponent.getTopLevelAncestor.getLocationOnScreen.x
      p
    }

    val manager = HintManagerImpl.getInstanceImpl

    manager.showEditorHint(hint, editor, point,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, hint, constraint).setContentActive(false))
  }

  override def isDisposed: Boolean = !hint.isVisible

  override def cancel(): Unit = {
    hint.hide()
  }

  override def addHideListener(action: () => Unit): Unit = hint.addHintListener(_ => action())
}

private object HintUI {
  def apply(message: String, editor: Editor): TooltipUI = {
    val hint = {
      // TODO Why HTML is rewritten by com.intellij.ide.IdeTooltipManager.initPane(com.intellij.util.ui.Html, com.intellij.ui.HintHint, javax.swing.JLayeredPane) ?
      val label = if (message.contains(ScalaBundle.message("type.mismatch.dot"))) new JLabel(message) else HintUtil.createInformationLabel(message)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    new HintUI(message, hint)
  }
}
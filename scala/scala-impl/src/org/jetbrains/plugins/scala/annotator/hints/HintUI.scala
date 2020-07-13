package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Component
import java.awt.Point

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.JBUI
import javax.swing.JLabel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle

private class HintUI(override val message: String,
                     hint: LightweightHint)
  extends TooltipUI {

  override protected def showImpl(editor: Editor, mousePoint: Point, inlayOffset: Int): Unit = {
    val constraint = HintManager.ABOVE

    val manager = HintManagerImpl.getInstanceImpl

    def xOnScreen(c: Component) = c.getLocationOnScreen.x

    val deltaX = xOnScreen(editor.getContentComponent) - xOnScreen(editor.getContentComponent.getTopLevelAncestor)
    val x = mousePoint.x + deltaX

    val y = HintManagerImpl.getHintPosition(hint, editor, editor.xyToVisualPosition(mousePoint), constraint).y

    val point = new Point(x, y)

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
  def apply(@Nls message: String, editor: Editor): TooltipUI = {
    val hint = {
      // TODO Why HTML is rewritten by com.intellij.ide.IdeTooltipManager.initPane(com.intellij.util.ui.Html, com.intellij.ui.HintHint, javax.swing.JLayeredPane) ?
      val label = if (message.contains(ScalaBundle.message("type.mismatch.dot"))) new JLabel(message) else HintUtil.createInformationLabel(message)
      label.setBorder(JBUI.Borders.empty(6, 6, 5, 6))
      new LightweightHint(label)
    }

    new HintUI(message, hint)
  }
}
package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Point
import java.awt.event.MouseEvent

import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Ref
import com.intellij.ui.HintHint

private class PopupUI(popup: JBPopup) extends TooltipUI {
  override protected def showImpl(inlay: Inlay[_], e: MouseEvent): Unit = {
    val editor = inlay.getEditor
    val line = inlay.getVisualPosition.line
    val y = editor.visualLineToY(line) + editor.getContentComponent.getLocationOnScreen.y + editor.getLineHeight
    popup.showInScreenCoordinates(editor.getComponent, new Point(e.getXOnScreen, y))
  }

  override def hide(): Unit = popup.closeOk(null)

  override def addHideListener(action: () => Unit): Unit = popup.addListener(new JBPopupListener {
    override def onClosed(event: LightweightWindowEvent): Unit = action()
  })

  override def isVisible: Boolean = popup.isVisible
}

private object PopupUI {
  def apply(message: String, tooltipAction: TooltipAction, editor: Editor): PopupUI = {
    val provider = editor.getMarkupModel.asInstanceOf[EditorMarkupModel].getErrorStripTooltipRendererProvider
    val renderer = provider.calcTooltipRenderer(message, tooltipAction, -1).asInstanceOf[LineTooltipRenderer]

    val hintHint = new HintHint()
    hintHint.setAwtTooltip(true)

    val dummyHint = renderer.createHint(editor, new Point(0, 0), false, ErrorTooltip.tooltipGroup, hintHint, true, true, null)
    val component = dummyHint.getComponent
    component.setBackground(hintHint.getTextBackground)
    component.setOpaque(true)

    val popupRef: Ref[JBPopup] = Ref.create()
    val popup = JBPopupFactory.getInstance.createComponentPopupBuilder(component, null)
      .setFocusable(true)
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .setKeyEventHandler { _ =>
        popupRef.get().closeOk(null)
        false
      }
      .createPopup
    popupRef.set(popup)

    new PopupUI(popup)
  }
}
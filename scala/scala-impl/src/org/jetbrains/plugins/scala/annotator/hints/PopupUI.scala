package org.jetbrains.plugins.scala.annotator.hints

import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit

import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.ui.HintHint
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt

private class PopupUI(override val message: String,
                      popup: JBPopup)
  extends TooltipUI {

  override protected def showImpl(inlay: Inlay[_], e: MouseEvent): Unit = {
    val editor = inlay.getEditor
    val line = inlay.getVisualPosition.line
    val editorContent = editor.getContentComponent.getLocationOnScreen
    val x = inlay.getBounds.x + editorContent.x
    val y = editor.visualLineToY(line) + editorContent.y + editor.getLineHeight
    popup.showInScreenCoordinates(editor.getComponent, new Point(x, y))
    popup.setUiVisible(false)

    val makeVisible: Runnable = () => {
      if (!popup.isDisposed)
        popup.setUiVisible(true)
    }

    AppExecutorUtil.getAppScheduledExecutorService.schedule(makeVisible, 300L, TimeUnit.MILLISECONDS)
  }

  override def cancel(): Unit = popup.cancel()

  override def addHideListener(action: () => Unit): Unit = popup.addListener(new JBPopupListener {
    override def onClosed(event: LightweightWindowEvent): Unit = action()
  })

  override def isDisposed: Boolean = popup.isDisposed
}

private object PopupUI {
  def apply(message: String, action: IntentionAction, element: PsiElement, editor: Editor): PopupUI = {
    val provider = editor.getMarkupModel.asInstanceOf[EditorMarkupModel].getErrorStripTooltipRendererProvider
    val uiRef = Ref.create[PopupUI]()
    val tooltipAction = new MyTooltipAction(action, element, uiRef)
    val renderer = provider.calcTooltipRenderer(message, tooltipAction, -1).asInstanceOf[LineTooltipRenderer]

    val hintHint = new HintHint()
    hintHint.setAwtTooltip(true)

    val dummyHint = renderer.createHint(editor, new Point(0, 0), false, ErrorTooltip.tooltipGroup, hintHint, true, true, null)
    val component = dummyHint.getComponent
    component.setBackground(hintHint.getTextBackground)
    component.setOpaque(true)

    val popup = JBPopupFactory.getInstance.createComponentPopupBuilder(component, null)
      .setFocusable(true)
      .setCancelOnClickOutside(true)
      .setModalContext(false)
      .createPopup

    popup.addListener(new JBPopupListener {
      override def onClosed(event: LightweightWindowEvent): Unit = dummyHint.hide()
    })

    val ui = new PopupUI(message, popup)
    uiRef.set(ui)
    ui
  }

  private class MyTooltipAction(action: IntentionAction, element: PsiElement, ui: Ref[PopupUI]) extends TooltipAction {

    override def getText: String = action.getText

    override def execute(editor: Editor, event: InputEvent): Unit = {
      ui.get().cancel()
      if (element.isValid) {
        action.invoke(element.getProject, editor, element.getContainingFile)
      }
    }

    override def showAllActions(editor: Editor): Unit = {
      ui.get().cancel()
      if (element.isValid) {
        editor.getCaretModel.moveToOffset(element.endOffset)
        new ShowIntentionActionsHandler().invoke(element.getProject, editor, element.getContainingFile, true)
      }
    }
  }
}
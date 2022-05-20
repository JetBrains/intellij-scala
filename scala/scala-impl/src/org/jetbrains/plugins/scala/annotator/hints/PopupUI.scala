package org.jetbrains.plugins.scala.annotator.hints

import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.{EditorMarkupModel, TooltipAction}
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory, JBPopupListener, LightweightWindowEvent}
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.ui.HintHint
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.autoImport.quickFix.PopupPosition
import org.jetbrains.plugins.scala.extensions.PsiElementExt

import java.awt.Point
import java.awt.event.{InputEvent, KeyEvent, KeyListener}
import java.util.concurrent.TimeUnit

private class PopupUI(override val message: String,
                      val popup: JBPopup)
  extends TooltipUI {

  override protected def showImpl(editor: Editor, mousePoint: Point, inlayOffset: Int): Unit = {
    val underLineY = editor.offsetToXY(inlayOffset).y + editor.getLineHeight
    val popupWidth = popup.getContent.getPreferredSize.width

    val point = new Point(
      mousePoint.x + editor.getContentComponent.getLocationOnScreen.x - popupWidth / 8,
      underLineY + editor.getContentComponent.getLocationOnScreen.y
    )

    PopupPosition.at(point).showPopup(popup, editor)
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
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnMouseOutCallback(event => component.contains(event.getPoint))
      .setModalContext(false)
      .createPopup

    val keyListener: KeyListener = new KeyListener {
      private def cancel(): Unit = {
        popup.cancel()
        editor.getContentComponent.removeKeyListener(this)
      }
      override def keyTyped(e: KeyEvent): Unit = cancel()
      override def keyPressed(e: KeyEvent): Unit = ()
      override def keyReleased(e: KeyEvent): Unit = ()
    }
    editor.getContentComponent.addKeyListener(keyListener)

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
      val popupUi = ui.get()
      val popupLocation = popupUi.popup.getLocationOnScreen
      popupUi.cancel()
      if (element.isValid) {
        PopupPosition.withCustomPopupLocation(editor, popupLocation) {
          action.invoke(element.getProject, editor, element.getContainingFile)
        }
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
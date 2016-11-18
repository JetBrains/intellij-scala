package org.jetbrains.plugins.scala
package actions

import java.awt.Point
import java.awt.event.{MouseEvent, MouseMotionAdapter}

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.actionSystem.{Presentation, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.editor.Editor
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

object ScalaActionUtil {
  def enablePresentation(presentation: Presentation): Unit = {
    presentation setEnabled true
    presentation setVisible true
  }
  
  def disablePresentation(presentation: Presentation): Unit = {
    presentation setEnabled false
    presentation setVisible false
  }
  
  def enableAndShowIfInScalaFile(e: AnActionEvent) {
    val presentation = e.getPresentation
    
    @inline def enable(): Unit = enablePresentation(presentation)
    
    @inline def disable(): Unit = disablePresentation(presentation)
    
    try {
      val dataContext = e.getDataContext
      val file = CommonDataKeys.PSI_FILE.getData(dataContext)
      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case _: Exception => disable()
    }
  }

  def showHint(editor: Editor, text: String) {
    val label = HintUtil.createInformationLabel(text)
    label.setFont(UIUtil.getLabelFont)

    val hint: LightweightHint = new LightweightHint(label)

    val hintManager: HintManagerImpl = HintManagerImpl.getInstanceImpl

    label.addMouseMotionListener(new MouseMotionAdapter {
      override def mouseMoved(e: MouseEvent) {
        hintManager.hideAllHints()
      }
    })

    val position = editor.getCaretModel.getLogicalPosition
    val p: Point = HintManagerImpl.getHintPosition(hint, editor, position, HintManager.ABOVE)

    hintManager.showEditorHint(hint, editor, p,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false)
  }
}

package org.jetbrains.plugins.scala
package actions

import java.awt.Point
import java.awt.event.{MouseEvent, MouseMotionAdapter}
import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.actionSystem.{ActionManager, AnAction, AnActionEvent, CommonDataKeys, Presentation}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

object ScalaActionUtil {

  def enablePresentation(presentation: Presentation): Unit = {
    presentation.setEnabled(true)
    presentation.setVisible(true)
  }
  
  def disablePresentation(presentation: Presentation): Unit = {
    presentation.setEnabled(false)
    presentation.setVisible(false)
  }

  @inline def enablePresentation(e: AnActionEvent): Unit = enablePresentation(e.getPresentation)

  @inline def disablePresentation(e: AnActionEvent): Unit = disablePresentation(e.getPresentation)

  def enableAndShowIfInScalaFile(e: AnActionEvent): Unit = try {
    getFileFrom(e) match {
      case Some(_: ScalaFile) => enablePresentation(e)
      case _ => disablePresentation(e)
    }
  } catch {
    case c: ControlFlowException => throw c
    case _: Exception => disablePresentation(e)
  }

  def getFileFrom(e: AnActionEvent): Option[PsiFile] = Option(CommonDataKeys.PSI_FILE.getData(e.getDataContext))
  
  def showHint(editor: Editor, @Nls text: String): Unit = {
    val label = HintUtil.createInformationLabel(text)
    label.setFont(StartupUiUtil.getLabelFont)

    val hint: LightweightHint = new LightweightHint(label)

    val hintManager: HintManagerImpl = HintManagerImpl.getInstanceImpl

    label.addMouseMotionListener(new MouseMotionAdapter {
      override def mouseMoved(e: MouseEvent): Unit = {
        hintManager.hideAllHints()
      }
    })

    val position = editor.getCaretModel.getLogicalPosition
    val p: Point = HintManagerImpl.getHintPosition(hint, editor, position, HintManager.ABOVE)

    hintManager.showEditorHint(hint, editor, p,
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false)
  }
}

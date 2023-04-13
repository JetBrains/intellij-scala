package org.jetbrains.plugins.scala.actions

import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, HintUtil}
import com.intellij.openapi.actionSystem.{AnActionEvent, CommonDataKeys, Presentation}
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.awt.Point
import java.awt.event.{MouseEvent, MouseMotionAdapter}

object ScalaActionUtil {

  @deprecated("Use `event.getPresentation.setEnabledAndVisible(true)` instead")
  @inline def enablePresentation(e: AnActionEvent): Unit = e.getPresentation.setEnabledAndVisible(true)

  @deprecated("Use `event.getPresentation.setEnabledAndVisible(false)` instead")
  @inline def disablePresentation(e: AnActionEvent): Unit = e.getPresentation.setEnabledAndVisible(false)

  def enableAndShowIfInScalaFile(e: AnActionEvent): Unit = try {
    val isInScalaFile = getFileFrom(e).exists(_.is[ScalaFile])
    e.getPresentation.setEnabledAndVisible(isInScalaFile)
  } catch {
    case c: ControlFlowException =>
      throw c
    case _: Exception =>
      e.getPresentation.setEnabledAndVisible(false)
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

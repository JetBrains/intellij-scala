package org.jetbrains.plugins.scala
package actions

import _root_.com.intellij.codeInsight.hint.{HintUtil, HintManager, HintManagerImpl}
import _root_.com.intellij.codeInsight.{TargetElementUtilBase}
import _root_.com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, PlatformDataKeys}
import _root_.com.intellij.psi._
import _root_.com.intellij.psi.util.PsiUtilBase
import _root_.com.intellij.util.ui.UIUtil
import _root_.java.awt.event.{MouseEvent, MouseMotionAdapter}
import _root_.java.awt.{Point}
import _root_.com.intellij.openapi.ui.{MultiLineLabelUI}
import _root_.com.intellij.openapi.editor.{Editor}
import _root_.com.intellij.ui.LightweightHint
import _root_.org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import _root_.org.jetbrains.plugins.scala.ScalaBundle
import lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import lang.psi.api.statements.params.ScParameter
import lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}

/**
 * Pavel.Fatin, 16.04.2010
 */

class ShowTypeInfoAction extends AnAction(ScalaBundle.message("type.info")) {
  def actionPerformed(e: AnActionEvent) {
    val context = e.getDataContext
    val editor = PlatformDataKeys.EDITOR.getData(context)
    
    if(editor == null) return
    
    val file = PsiUtilBase.getPsiFileInEditor(editor, PlatformDataKeys.PROJECT.getData(context))

    if (editor.getSelectionModel.hasSelection) {
      // maybe we should display type for selection
    } else {
      val offest = TargetElementUtilBase.adjustOffset(editor.getDocument,
        editor.logicalPositionToOffset(editor.getCaretModel.getLogicalPosition))

      val hint = file.findReferenceAt(offest) match {
        case Resolved(e) => typeOf(e)
        case _ => {
          file.findElementAt(offest) match {
            case Parent(p) => typeOf(p)
            case _ => None
          }
        }
      }

      hint.foreach(showTypeHint(editor, _))
    }
  }

  val typeOf: PsiElement => Option[String] = {
    case e: ScFunction => e.returnType.toOption.map(_.presentableText)
    case e: ScBindingPattern => e.getType(TypingContext.empty).toOption.map(_ .presentableText)
    case e: ScParameter => e.getRealParameterType(TypingContext.empty).toOption.map(_ .presentableText)
    case e: PsiMethod => e.getReturnType.toOption.map(_ getPresentableText)
    case e: PsiVariable => e.getType.toOption.map(_ getPresentableText)
    case _ => None
  }

  def showTypeHint(editor: Editor, text: String) {
    var label = HintUtil.createInformationLabel(text)
    label.setUI(new MultiLineLabelUI)
    label.setFont(UIUtil.getLabelFont)

    val hint: LightweightHint = new LightweightHint(label)

    val hintManager: HintManagerImpl = HintManagerImpl.getInstanceImpl

    label.addMouseMotionListener(new MouseMotionAdapter {
      override def mouseMoved(e: MouseEvent): Unit = {
        hintManager.hideAllHints
      }
    })

    val position = editor.getCaretModel.getLogicalPosition
    var p: Point = HintManagerImpl.getHintPosition(hint, editor, position, HintManager.ABOVE)

    hintManager.showEditorHint(hint, editor, p, 
      HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0, false)
  }
}




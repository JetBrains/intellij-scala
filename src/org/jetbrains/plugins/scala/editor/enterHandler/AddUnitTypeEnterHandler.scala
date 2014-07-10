package org.jetbrains.plugins.scala
package editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiDocumentManager, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
 * User: Dmitry.Naydanov
 * Date: 09.07.14.
 */
class AddUnitTypeEnterHandler extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    val project = file.getProject
    val scalaSettings = ScalaCodeStyleSettings.getInstance(project)
    if (!scalaSettings.ENFORCE_PROCEDURE_SYNTAX_FOR_UNIT) return Result.Continue

    val caretModel = editor.getCaretModel
    val document = editor.getDocument
    val offset = caretModel.getOffset
    val element = file.findElementAt(offset)

    if (element == null) return Result.Continue

    @inline def checkBlock2(block: ScBlockExpr) = {
      val children: Array[PsiElement] = block.getChildren
      children.length == 3 && children.apply(1).isInstanceOf[PsiWhiteSpace] && children.apply(1).getText.count(_ == '\n') == 2
    }

    element.getParent match {
      case block: ScBlockExpr if checkBlock2(block) =>
        (block.getParent, block.getPrevSiblingNotWhitespace) match {
          case (funDef: ScFunctionDefinition, prev: ScalaPsiElement) =>
            if (funDef.findFirstChildByType(ScalaTokenTypes.tASSIGN) == null)
              extensions.inWriteAction {
                document.insertString(prev.getTextRange.getEndOffset, ": Unit =")
                PsiDocumentManager.getInstance(project).commitDocument(document)
              }
          case _ =>
        }
      case _ =>
    }

    Result.Default
  }
}

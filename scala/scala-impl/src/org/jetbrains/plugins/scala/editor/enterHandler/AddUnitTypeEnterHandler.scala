package org.jetbrains.plugins.scala
package editor
package enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class AddUnitTypeEnterHandler extends EnterHandlerDelegateAdapter {
  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = {
    if (!isApplicable(file, editor)) return Result.Continue

    val document = editor.getDocument

    editor.commitDocument(file.getProject)

    val element = file.findElementAt(editor.offset)

    if (element == null) return Result.Continue

    @inline def checkBlock2(block: ScBlockExpr) = {
      val children: Array[PsiElement] = block.getChildren
      children.length == 3 && children(1).is[PsiWhiteSpace] && children(1).getText.count(_ == '\n') == 2
    }

    element.getParent match {
      case block: ScBlockExpr if checkBlock2(block) =>
        (block.getParent, block.getPrevSiblingNotWhitespace) match {
          case (funDef: ScFunctionDefinition, prev: ScalaPsiElement) =>
            if (funDef.findFirstChildByType(ScalaTokenTypes.tASSIGN).isEmpty)
              extensions.inWriteAction {
                document.insertString(prev.getTextRange.getEndOffset, ": Unit =")
              }
          case _ =>
        }
      case _ =>
    }

    Result.Default
  }

  private def isApplicable(file: PsiFile, editor: Editor): Boolean = {
    val project = file.getProject
    val settings = ScalaCodeStyleSettings.getInstance(project)

    file.is[ScalaFile] &&
      settings.ENFORCE_FUNCTIONAL_SYNTAX_FOR_UNIT &&
      prevNonWhitespace(editor) == '{'
  }

  private def prevNonWhitespace(editor: Editor): Char = {
    val chars = editor.getDocument.getImmutableCharSequence
    var offset = editor.offset
    var found = ' '
    while (found.isWhitespace && offset > 0) {
      offset -= 1
      found = chars.charAt(offset)
    }
    found
  }
}

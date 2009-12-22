package org.jetbrains.plugins.scala.highlighter.usages

import com.intellij.codeInsight.highlighting.{HighlightUsagesHandlerBase, HighlightUsagesHandlerFactory}
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.12.2009
 */

class ScalaHighlightUsagesHandlerFactory extends  HighlightUsagesHandlerFactory {
  def createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase[_ <: PsiElement] = {
    if (!file.isInstanceOf[ScalaFile]) return null
    val element: PsiElement = file.findElementAt(editor.getCaretModel.getOffset)
    if (element == null || element.getNode == null) return null
    element.getNode.getElementType match {
      case ScalaTokenTypes.kRETURN => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])
        if (fun != null) return new ScalaHighlightExitPointsHandler(fun, editor, file)
      }
      case ScalaTokenTypes.kDEF => {
        val fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunction])
        fun match {
          case d: ScFunctionDefinition => return new ScalaHighlightExitPointsHandler(d, editor, file)
          case _ =>
        }
      }
      case _ =>
    }
    return null
  }
}
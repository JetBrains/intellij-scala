package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScWhile}

/**
 * @author Dmitry.Naydanov
 * @author Ksenia.Sautina
 * @since 2/5/13
 */
class ScalaMissingWhileBodyFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhile], false)
    if (whileStatement == null) return NoOperation

    val doc = editor.getDocument
    val body = whileStatement.body.orNull

    whileStatement.body match {
      case Some(_: ScBlockExpr) => NoOperation
      case Some(_) if startLine(doc, body) == startLine(doc, whileStatement) && whileStatement.condition.isDefined => NoOperation
      case _ =>
        whileStatement.getRightParenthesis map {
          case rParenth =>
            moveToEnd(editor, rParenth)

            doc.insertString(rParenth.getTextRange.getEndOffset, " {}")

            WithEnter(2)
        } getOrElse NoOperation
    }
  }
}


package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScWhile}

class ScalaMissingWhileBodyFixer extends ScalaFixer {
  override def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement): OperationPerformed = {
    val whileStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScWhile], false)
    if (whileStatement == null) return NoOperation

    val doc = editor.getDocument
    val body = whileStatement.expression.orNull

    whileStatement.expression match {
      case Some(_: ScBlockExpr) => NoOperation
      case Some(_) if startLine(doc, body) == startLine(doc, whileStatement) && whileStatement.condition.isDefined => NoOperation
      case _ =>
        whileStatement.rightParen map {
          rParenth =>
            moveToEnd(editor, rParenth)

            doc.insertString(rParenth.getTextRange.getEndOffset, " {}")

            WithEnter(2)
        } getOrElse NoOperation
    }
  }
}


package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}

abstract class ScalaWithTrySurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(tryNode: ASTNode): Option[TextRange] =
    unwrapParenthesis(tryNode) match {
      case Some(stmt: ScTry) =>
        getRange(stmt.toIndentationBasedSyntax)
      case _ => None
    }

  override protected val isApplicableToMultipleElements: Boolean = true

  protected def getRangeToDelete(tryStmt: ScTry): Option[TextRange]

  protected def arrow(elements: Array[PsiElement]): String =
    if (elements.isEmpty) "=>"
    else ScalaPsiUtil.functionArrow(elements.head.getProject)

  private def getRange(tryStmt: ScTry): Option[TextRange] = for {
    file          <- tryStmt.containingFile
    rangeToDelete <- getRangeToDelete(tryStmt)
    document       = file.getFileDocument
    _              = document.deleteString(rangeToDelete.getStartOffset, rangeToDelete.getEndOffset)
  } yield TextRange.from(rangeToDelete.getStartOffset, 0)
}

abstract class ScalaWithTryCatchSurrounderBase extends ScalaWithTrySurrounderBase {
  override protected def getRangeToDelete(tryStmt: ScTry): Option[TextRange] =
    tryStmt.catchBlock match {
      case Some(ScCatchBlock(clauses)) =>
        clauses.caseClause.pattern
          .flatMap(_.forcePostprocessAndRestore)
          .map(_.getTextRange)
      case _ => None
    }
}

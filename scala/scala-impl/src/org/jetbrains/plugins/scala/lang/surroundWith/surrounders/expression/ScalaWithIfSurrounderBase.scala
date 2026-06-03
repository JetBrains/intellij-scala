package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIf

abstract class ScalaWithIfSurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(nodeWithIfNode: ASTNode): Option[TextRange] =
    unwrapParenthesis(nodeWithIfNode) match {
      case Some(stmt: ScIf) =>
        getRange(stmt.toIndentationBasedSyntax)
      case _ => None
    }

  protected def getRange(ifStmt: ScIf): Option[TextRange] = for {
    file      <- ifStmt.containingFile
    condition <- ifStmt.condition.flatMap(_.forcePostprocessAndRestore)
    offset     = condition.startOffset
    document   = file.getFileDocument
    _          = document.deleteString(offset, condition.endOffset)
  } yield TextRange.from(offset, 0)
}

abstract class ScalaWithIfConditionSurrounderBase extends ScalaWithIfSurrounderBase {
  override protected def getRange(ifStmt: ScIf): Option[TextRange] = for {
    thenExpr <- ifStmt.thenExpression.flatMap(_.forcePostprocessAndRestore)
    offset    = thenExpr.startOffset + 1
  } yield TextRange.from(offset, 0)

  override def isApplicable(element: PsiElement): Boolean = isBooleanExpression(element)
}

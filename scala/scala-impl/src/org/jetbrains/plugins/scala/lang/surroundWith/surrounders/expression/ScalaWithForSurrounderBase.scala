package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor

abstract class ScalaWithForSurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(withForNode: ASTNode): Option[TextRange] =
    unwrapParenthesis(withForNode) match {
      case Some(stmt: ScFor) =>
        getRange(stmt.toIndentationBasedSyntax)
      case _ => None
    }

  protected def getRange(forStmt: ScFor): Option[TextRange] = for {
    file    <- forStmt.containingFile
    enums   <- forStmt.enumerators.flatMap(_.forcePostprocessAndRestore)
    offset   = enums.startOffset
    document = file.getFileDocument
    _        = document.deleteString(offset, enums.endOffset)
  } yield TextRange.from(offset, 0)

  override protected val isApplicableToMultipleElements: Boolean = true
}

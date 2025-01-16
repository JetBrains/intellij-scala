package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScNamedTupleExprComponentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleExprComponent {
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
  override def expr: Option[ScExpression] = findChild[ScExpression]
  override def `type`(): TypeResult = this.flatMapType(expr)
  override def nameId: NameId.Placed =
    nameElement match {
      case Some(identifier) => new NameId.Name(identifier)
      case None => new NameId.Error(findFirstChildByType(TokenType.ERROR_ELEMENT).get)
    }
}

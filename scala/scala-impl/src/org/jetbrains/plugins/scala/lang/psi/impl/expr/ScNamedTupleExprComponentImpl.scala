package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

final class ScNamedTupleExprComponentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleExprComponent {
  override def nameId: PsiElement = nameElement.orNull
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
  override def expr: Option[ScExpression] = findChild[ScExpression]
  override def `type`(): TypeResult = this.flatMapType(expr)
}

package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScNamedTuplePatternComponent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedTuplePatternComponentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTuplePatternComponent {
  override def nameId: NameId.Placed = {
    findFirstChildByType(ScalaTokenTypes.tIDENTIFIER) match {
      case Some(id) => new NameId.Name(id)
      case None => new NameId.Error(findFirstChildByType(TokenType.ERROR_ELEMENT).get)
    }
  }
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def `type`(): TypeResult = this.flatMap(subPattern)(_.`type`())
}
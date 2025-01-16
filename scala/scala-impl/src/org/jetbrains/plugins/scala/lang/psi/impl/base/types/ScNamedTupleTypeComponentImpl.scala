package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScNamedTupleTypeComponent
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScNamedTupleTypeComponentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleTypeComponent {
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
  override def nameId: NameId.Placed =
    nameElement match {
      case Some(identifier) => new NameId.Name(identifier)
      case None => new NameId.Error(findFirstChildByType(TokenType.ERROR_ELEMENT).get)
    }

  override protected val typeName: String = "NamedTupleTypeComponent"

  override protected def innerType: TypeResult = this.flatMapType(typeElement)
}

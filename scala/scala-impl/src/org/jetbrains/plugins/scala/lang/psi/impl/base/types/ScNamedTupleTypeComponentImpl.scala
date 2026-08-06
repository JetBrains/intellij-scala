package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScNamedTupleTypeComponent
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScNamedTupleTypeComponentImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleTypeComponent {
  override def nameElement: Option[PsiElement] = findFirstChildByType(ScalaTokenTypes.tIDENTIFIER)
  override def nameId: PsiElement = nameElement.orNull

  override protected val typeName: String = "NamedTupleTypeComponent"

  override protected def innerType: TypeResult = this.flatMapType(typeElement)
}

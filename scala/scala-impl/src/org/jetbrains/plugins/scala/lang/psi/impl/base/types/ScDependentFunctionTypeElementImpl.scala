package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{
  ScDependentFunctionTypeElement,
  ScTypeElement
}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.extensions._

class ScDependentFunctionTypeElementImpl(node: ASTNode)
    extends ScalaPsiElementImpl(node)
    with ScDependentFunctionTypeElement {

  override def paramTypeElement: ScParameterClause      = findChildByClassScala(classOf[ScParameterClause])
  override def returnTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]

  override protected def innerType: TypeResult =
    Failure("Dependent function types are not yet supported")
}

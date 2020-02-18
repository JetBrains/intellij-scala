package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{
  ScPolyFunctionTypeElement,
  ScTypeElement
}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.extensions._

class ScPolyFunctionTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScPolyFunctionTypeElement {
  override protected def innerType: TypeResult =
    Failure("Polymorphic function types are not yet supported")

  override def resultTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]
}


package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTypeLambdaTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScTypeLambdaTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScTypeLambdaTypeElement {

  override protected def innerType: TypeResult =
    resultType.map(ScTypePolymorphicType(_, typeParameters.map(TypeParameter(_))))

  override def resultTypeElement: Option[ScTypeElement] = findChild[ScTypeElement]
  override def resultType: TypeResult                   = this.flatMapType(resultTypeElement)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitTypeLambdaTypeElement(this)
}

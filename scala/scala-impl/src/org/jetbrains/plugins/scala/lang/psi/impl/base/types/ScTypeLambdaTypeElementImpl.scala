package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeElement, ScTypeLambdaTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScTypeLambdaTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
    with ScTypeLambdaTypeElement {

  override protected def innerType: TypeResult =
    Failure("Scala 3 type are not yet supported")

  override def resultTypeElement: Option[ScTypeElement] = findChild(classOf[ScTypeElement])
  override def resultType: TypeResult                   = this.flatMapType(resultTypeElement)
  override def lowerBound: TypeResult                   = resultType
  override def upperBound: TypeResult                   = resultType

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitTypeLambdaTypeElement(this)
}

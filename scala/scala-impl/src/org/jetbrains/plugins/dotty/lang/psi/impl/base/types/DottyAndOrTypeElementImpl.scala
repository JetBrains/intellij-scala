package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

/**
  * @author adkozlov
  */
abstract class DottyAndOrTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement {

  protected def innerTypeValue: ScType

  protected def types(default: ScType): Seq[ScType] =
    Seq(
      leftTypeElement.`type`(),
      rightTypeElement match {
        case Some(typeElement) => typeElement.`type`()
        case _ => Success(api.Nothing)
      }
    ).map(_.getOrElse(default))

  override protected def innerType: Success[ScType] = Success(innerTypeValue)
}

class DottyAndTypeElementImpl(node: ASTNode) extends DottyAndOrTypeElementImpl(node) {
  override val typeName = "AndType"

  override protected def innerTypeValue: ScType =
    DottyAndType(types(api.Nothing))
}

class DottyOrTypeElementImpl(node: ASTNode) extends DottyAndOrTypeElementImpl(node) {
  override val typeName = "OrType"

  override protected def innerTypeValue: ScType =
    DottyOrType(types(api.Any))
}

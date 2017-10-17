package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScTypeElement, ScTypeElementExt}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
  * @author adkozlov
  */
abstract class DottyAndOrTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScInfixTypeElement {
  protected val defaultType: ScType

  protected def apply: Seq[ScType] => ScType

  protected def innerType: TypeResult[ScType] = {
    def lift(typeElement: ScTypeElement) = typeElement.getType()

    val rightType = rightTypeElement match {
      case Some(typeElement) => lift(typeElement)
      case _ => this.success(Nothing)
    }
    collectFailures(Seq(lift(leftTypeElement), rightType), defaultType)(apply)
  }
}

class DottyAndTypeElementImpl(node: ASTNode) extends DottyAndOrTypeElementImpl(node) {
  override val typeName = "AndType"

  override protected val defaultType = Nothing

  override protected def apply = DottyAndType(_)
}

class DottyOrTypeElementImpl(node: ASTNode) extends DottyAndOrTypeElementImpl(node) {
  override val typeName = "OrType"

  override protected val defaultType = Any

  override protected def apply = DottyOrType(_)
}

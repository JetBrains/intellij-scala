package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TupleType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author ilyas, Alexander Podkhalyuzin
 */
class ScTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTuple {

  protected override def innerType: TypeResult = {
    val result = exprs.map(_.`type`().getOrAny) match {
      case Seq() => Unit
      case components => TupleType(components.map(ScLiteralType.widen))
    }
    Right(result)
  }

  override def toString: String = "Tuple"
}
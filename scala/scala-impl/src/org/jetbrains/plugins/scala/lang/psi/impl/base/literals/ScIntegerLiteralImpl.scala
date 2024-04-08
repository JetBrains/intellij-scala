package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScIntegerLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends NumericLiteralImplBase(node, toString)
    with ScIntegerLiteral {

  override protected def wrappedValue(value: Integer): ScLiteral.Value[Integer] =
    ScIntegerLiteralImpl.Value(value)

  override protected def fallbackType: ScType = api.Int

  override protected def parseNumber(text: String): Integer =
    literals.parseInteger(text, stripLeading0 = this.isInScala3Module)

  override private[psi] def unwrappedValue(value: Integer) =
    value.intValue
}

object ScIntegerLiteralImpl {
  final case class Value(override val value: Integer)
    extends NumericLiteralImplBase.Value(value) {

    override def negate: NumericLiteralImplBase.Value[Integer] = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Int
  }
}
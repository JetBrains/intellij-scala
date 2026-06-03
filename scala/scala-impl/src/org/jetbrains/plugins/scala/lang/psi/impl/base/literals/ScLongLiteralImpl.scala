package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScLongLiteral
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import java.lang
import java.lang.{Long => JLong}

final class ScLongLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends NumericLiteralImplBase(node, toString)
    with ScLongLiteral {

  override protected def wrappedValue(value: JLong): ScLiteral.Value[lang.Long] =
    ScLongLiteralImpl.Value(value)

  override protected def fallbackType: ScType = api.Long

  override protected def parseNumber(text: String): JLong =
    literals.parseLong(text, stripLeading0 = this.isInScala3Module)

  override private[psi] def unwrappedValue(value: JLong) =
    value.longValue
}

object ScLongLiteralImpl {

  final case class Value(override val value: JLong)
    extends NumericLiteralImplBase.Value(value) {

    override def negate: NumericLiteralImplBase.Value[JLong] = Value(-value)

    override def presentation: String = super.presentation + 'L'

    override def wideType(implicit project: Project): ScType = api.Long
  }
}

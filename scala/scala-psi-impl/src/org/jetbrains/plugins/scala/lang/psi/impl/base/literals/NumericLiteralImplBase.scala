package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.util.text.LiteralFormatUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types.api.StdType

abstract class NumericLiteralImplBase(node: ASTNode,
                                      override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with ScLiteral.Numeric {

  protected def parseNumber(text: String): V

  override final def getValue: V = parseNumber {
    LiteralFormatUtil.removeUnderscores(getText)
  }
}

object NumericLiteralImplBase {

  abstract class Value[V <: Number](override val value: V)
    extends ScLiteral.Value[V](value) {

    def negate: Value[V]

    override def to(tpe: StdType): Option[ScLiteral.Value[_ <: Any]] = {
      val stdTypes = tpe.projectContext.stdTypes
      import stdTypes.*

      Some(tpe).collect {
        case Byte if value.longValue.isValidByte => ScIntegerLiteralImpl.Value(value.intValue)
        case Short if value.longValue.isValidShort => ScIntegerLiteralImpl.Value(value.intValue)
        case Int if value.longValue.isValidInt => ScIntegerLiteralImpl.Value(value.intValue)
        case Long => ScLongLiteralImpl.Value(value.longValue)
        case Float if isValidFloat(value) => ScFloatLiteralImpl.Value(value.floatValue)
        case Double if isValidDouble(value) => ScDoubleLiteralImpl.Value(value.doubleValue)
        case Char if value.longValue.isValidChar => ScCharLiteralImpl.Value(value.intValue.toChar)
      }
    }

    private def isValidFloat(v: V) =
      v.doubleValue >= scala.Float.MinValue && v.doubleValue <= scala.Float.MaxValue &&
        !v.floatValue.isNaN && !v.floatValue.isInfinite &&
        !(v.floatValue == 0.0f && v.doubleValue != 0.0)

    private def isValidDouble(v: V) =
      !v.doubleValue.isNaN && !value.doubleValue.isInfinite
  }
}

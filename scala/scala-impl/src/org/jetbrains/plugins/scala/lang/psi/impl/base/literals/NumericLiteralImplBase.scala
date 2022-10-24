package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.util.text.LiteralFormatUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

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
  }
}
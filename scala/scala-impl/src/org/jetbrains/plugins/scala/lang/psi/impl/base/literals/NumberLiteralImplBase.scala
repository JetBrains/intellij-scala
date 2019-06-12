package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.util.text.LiteralFormatUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

abstract class NumberLiteralImplBase(node: ASTNode,
                                     override val toString: String)
  extends ScLiteralImplBase(node, toString) {

  override protected type V >: Null <: Number

  protected def parseNumber(text: String): V

  override final def getValue: V = parseNumber {
    LiteralFormatUtil.removeUnderscores(getText)
  }
}

object NumberLiteralImplBase {

  abstract class Value[V <: Number](override val value: V)
    extends ScLiteral.Value[V](value) {

    def negate: Value[V]
  }
}
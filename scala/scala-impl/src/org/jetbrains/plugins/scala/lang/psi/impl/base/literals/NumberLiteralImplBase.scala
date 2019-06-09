package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.util.text.LiteralFormatUtil

abstract class NumberLiteralImplBase(node: ASTNode,
                                     override val toString: String)
  extends ScLiteralImplBase(node, toString) {

  protected type V >: Null <: Number

  protected def parseNumber(text: String): V

  override final def getValue: V = parseNumber {
    LiteralFormatUtil.removeUnderscores(getText)
  }
}

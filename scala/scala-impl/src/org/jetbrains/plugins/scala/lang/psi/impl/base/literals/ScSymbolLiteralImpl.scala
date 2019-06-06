package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, literals}

final class ScSymbolLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends QuotedLiteralImplBase(node, toString) with literals.ScSymbolLiteral {

  override protected def startQuote: String = ScLiteral.CharQuote

  override protected def endQuote: String = ""

  override def getValue: Symbol = super.getValue match {
    case symbolText: String => Symbol(symbolText)
    case _ => null
  }
}

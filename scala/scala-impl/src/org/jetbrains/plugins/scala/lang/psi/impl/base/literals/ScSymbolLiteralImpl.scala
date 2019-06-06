package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScSymbolLiteral

final class ScSymbolLiteralImpl(node: ASTNode,
                                override val toString: String)
  extends QuotedLiteralImplBase(node, toString) with ScSymbolLiteral {

  override protected def startQuote: String = QuotedLiteralImplBase.CharQuote

  override protected def endQuote: String = ""

  override def getValue: Symbol = super.getValue match {
    case symbolText: String => Symbol(symbolText)
    case _ => null
  }
}

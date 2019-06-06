package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import java.lang.{Boolean => JBoolean}

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScBooleanLiteral

final class ScBooleanLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with ScBooleanLiteral {

  override def getValue: JBoolean = {
    import lang.lexer.ScalaTokenTypes._
    getNode.getFirstChildNode.getElementType match {
      case `kTRUE` => JBoolean.TRUE
      case `kFALSE` => JBoolean.FALSE
    }
  }
}

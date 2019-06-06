package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScNullLiteral

final class ScNullLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with ScNullLiteral {

  override def getValue: Null = null
}

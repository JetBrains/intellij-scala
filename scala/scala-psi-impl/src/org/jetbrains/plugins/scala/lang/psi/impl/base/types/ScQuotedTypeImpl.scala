package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScQuotedType
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScExpressionImplBase

class ScQuotedTypeImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScQuotedType {
  override def toString: String = "QuotedType"
}

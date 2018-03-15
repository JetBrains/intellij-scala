package org.jetbrains.plugins.scala.lang.psi.api.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

trait ScLiteralTypeElement extends ScTypeElement {
  override protected val typeName = "LiteralType"

  def getLiteral: ScLiteral

  def getLiteralNode: ASTNode = getNode.getFirstChildNode.getFirstChildNode
}

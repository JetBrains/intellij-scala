package org.jetbrains.plugins.scala.lang.psi.api.base.types

import com.intellij.lang.ASTNode

trait ScLiteralTypeElement extends ScTypeElement {
  override protected val typeName = "LiteralType"

  def getLiteralText: String

  def getLiteralNode: ASTNode = getNode.getFirstChildNode.getFirstChildNode
}

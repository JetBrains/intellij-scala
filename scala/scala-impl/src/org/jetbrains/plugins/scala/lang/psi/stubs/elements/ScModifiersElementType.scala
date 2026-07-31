package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl

final class ScModifiersElementType extends ScStubElementType[ScModifierList]("modifiers") {
  override def createElement(node: ASTNode) = new ScModifierListImpl(node)
}

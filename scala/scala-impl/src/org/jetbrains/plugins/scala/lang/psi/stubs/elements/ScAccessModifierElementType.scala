package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl

final class ScAccessModifierElementType extends ScStubElementType[ScAccessModifier]("access modifier") {
  override def createElement(node: ASTNode): ScAccessModifier = new ScAccessModifierImpl(node)
}

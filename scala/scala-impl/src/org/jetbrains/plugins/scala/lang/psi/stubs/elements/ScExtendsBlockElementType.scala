package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl

final class ScExtendsBlockElementType extends ScStubElementType[ScExtendsBlock]("extends block") {
  override def createElement(node: ASTNode): ScExtendsBlock = new ScExtendsBlockImpl(node)
}

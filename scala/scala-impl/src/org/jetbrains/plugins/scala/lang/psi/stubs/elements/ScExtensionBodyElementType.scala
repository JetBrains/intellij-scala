package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionBodyImpl

final class ScExtensionBodyElementType extends ScStubElementType[ScExtensionBody]("extension body") {
  override def createElement(node: ASTNode): ScExtensionBody = new ScExtensionBodyImpl(node)
}

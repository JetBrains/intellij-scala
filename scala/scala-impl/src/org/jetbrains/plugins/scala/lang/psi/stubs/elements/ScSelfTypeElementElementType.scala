package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl

final class ScSelfTypeElementElementType extends ScStubElementType[ScSelfTypeElement]("self type element") {
  override def createElement(node: ASTNode) = new ScSelfTypeElementImpl(node)
}

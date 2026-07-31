package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScFieldIdImpl

final class ScFieldIdElementType extends ScStubElementType[ScFieldId]("field id") {
  override def createElement(node: ASTNode): ScFieldId = new ScFieldIdImpl(node)
}

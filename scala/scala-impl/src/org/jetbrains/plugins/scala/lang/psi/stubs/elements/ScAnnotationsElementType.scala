package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl

final class ScAnnotationsElementType extends ScStubElementType[ScAnnotations]("annotations") {
  override def createElement(node: ASTNode): ScAnnotations = new ScAnnotationsImpl(node)
}

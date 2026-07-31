package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationImpl

final class ScAnnotationElementType extends ScStubElementType[ScAnnotation]("annotation") {
  override def createElement(node: ASTNode): ScAnnotation = new ScAnnotationImpl(node)
}

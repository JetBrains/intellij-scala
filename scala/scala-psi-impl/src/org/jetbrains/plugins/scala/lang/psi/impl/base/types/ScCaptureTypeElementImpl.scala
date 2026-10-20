package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCaptureTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScCaptureTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCaptureTypeElement {
  override protected def innerType: TypeResult = innerElement.`type`()

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCaptureTypeElement(this)
  }
}

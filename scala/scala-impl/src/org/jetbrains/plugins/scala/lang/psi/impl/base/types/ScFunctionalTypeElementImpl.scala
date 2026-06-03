package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitFunctionalTypeElement(this)
  }
}
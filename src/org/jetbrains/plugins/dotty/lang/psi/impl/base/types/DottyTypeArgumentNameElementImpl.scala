package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyTypeArgumentNameElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

/**
  * @author adkozlov
  */
class DottyTypeArgumentNameElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with DottyTypeArgumentNameElement {
  override def name: String = node.getText
}

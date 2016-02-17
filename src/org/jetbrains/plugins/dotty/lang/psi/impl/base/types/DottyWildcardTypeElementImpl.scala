package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyDesugarizableTypeElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScWildcardTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScTypeBoundsOwnerImpl

/**
  * @author adkozlov
  */
class DottyWildcardTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScTypeBoundsOwnerImpl with ScWildcardTypeElement with DottyDesugarizableTypeElement {
  override def desugarizedText = ???
}

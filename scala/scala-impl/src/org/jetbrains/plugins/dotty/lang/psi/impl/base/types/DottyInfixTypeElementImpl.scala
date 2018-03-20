package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyDesugarizableTypeElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement

/**
  * @author adkozlov
  */
class DottyInfixTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScInfixTypeElement with DottyDesugarizableTypeElement

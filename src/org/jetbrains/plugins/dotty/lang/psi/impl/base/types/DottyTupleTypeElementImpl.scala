package org.jetbrains.plugins.dotty.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.dotty.lang.psi.api.base.types.DottyDesugarizableTypeElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTupleTypeElement

/**
  * @author adkozlov
  */
class DottyTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScTupleTypeElement with DottyDesugarizableTypeElement

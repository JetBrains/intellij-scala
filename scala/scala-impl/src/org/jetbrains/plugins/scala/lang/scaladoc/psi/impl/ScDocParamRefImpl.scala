package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocParamRef

class ScDocParamRefImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocParamRef{
  override def toString: String = "ScDocParamRef"
}
package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocMethodParameter

class ScDocMethodParameterImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodParameter{
  override def toString: String = "DocMethodParameter"
}
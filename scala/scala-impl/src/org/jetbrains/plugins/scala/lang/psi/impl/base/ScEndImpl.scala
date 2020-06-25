package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd

class ScEndImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnd {
  override def toString: String = "end " + endingElementDesignator

  override def endingElementDesignator: PsiElement = getLastChild
}

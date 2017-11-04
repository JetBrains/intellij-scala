package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander.Podkhalyuzin 
*/
class ScConstrBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlock {

  override def createMirror(text: String): PsiElement = {
    ScalaPsiElementFactory.createConstructorBodyWithContextFromText(text, getContext, this)
  }

  override def toString: String = "ConstructorBlock"
}
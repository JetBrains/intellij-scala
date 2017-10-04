package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander.Podkhalyuzin 
*/

class ScConstrBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScConstrBlock {
  override def toString: String = "ConstructorBlock"

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitConstrBlock(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitConstrBlock(this)
      case _ => super.accept(visitor)
    }
  }

  override def createMirror(text: String): PsiElement = {
    ScalaPsiElementFactory.createConstructorBodyWithContextFromText(text, getContext, this)
  }
}
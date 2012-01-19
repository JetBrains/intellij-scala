package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import api.expr._
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor

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
}
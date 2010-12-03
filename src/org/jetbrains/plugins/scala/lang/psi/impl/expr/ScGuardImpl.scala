package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/** 
* @author Alexander Podkhalyuzin
*/

class ScGuardImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGuard {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "Guard"

  def expr = findChild(classOf[ScExpression])
}
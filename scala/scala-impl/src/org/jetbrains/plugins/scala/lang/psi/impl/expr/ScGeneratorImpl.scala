package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, _}


/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScGeneratorImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScGenerator {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "Generator"

  def pattern: ScPattern = findChildByClass(classOf[ScPattern])

  def guard: ScGuard = findChildByClass(classOf[ScGuard])

  def rvalue: ScExpression = findChildByClass(classOf[ScExpression])
  
}
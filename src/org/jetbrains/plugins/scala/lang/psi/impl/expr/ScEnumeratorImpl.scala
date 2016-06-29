package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScEnumeratorImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerator {
  override def toString: String = "Enumerator"
  def pattern: ScPattern = findChildByClass(classOf[ScPattern])
  def rvalue: ScExpression = findChildByClass(classOf[ScExpression])

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }
}
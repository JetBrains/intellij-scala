package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForBindingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForBinding {
  override def toString: String = "ForBinding"
  override def pattern: ScPattern = findChildByClass(classOf[ScPattern])
  override def rvalue: ScExpression = findChildByClass(classOf[ScExpression])

  override def enumeratorToken: PsiElement = bindingToken

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }
}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForBinding extends ScEnumerator with ScPatterned {
  def expr: Option[ScExpression]

  def valKeyword: Option[PsiElement]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

object ScForBinding {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}
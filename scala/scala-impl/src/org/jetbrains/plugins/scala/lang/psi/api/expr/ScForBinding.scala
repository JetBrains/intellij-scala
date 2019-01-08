package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScForBinding extends ScEnumerator with ScPatterned {
  def expr: Option[ScExpression]

  def valKeyword: Option[PsiElement]

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitForBinding(this)
}

object ScForBinding {
  object expr {
    def unapply(forBinding: ScForBinding): Option[ScExpression] = forBinding.expr
  }
}
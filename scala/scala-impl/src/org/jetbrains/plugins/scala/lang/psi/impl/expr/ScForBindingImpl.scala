package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForBindingImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForBinding with ScEnumeratorImpl {
  override def pattern: ScPattern = findChildByClass(classOf[ScPattern])
  override def expr: Option[ScExpression] = Option(findChildByClass(classOf[ScExpression]))

  override def valKeyword: Option[PsiElement] =
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)

  override def enumeratorToken: Option[PsiElement] =
    Option(findFirstChildByType(ScalaTokenTypes.tASSIGN))

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ForBinding"
}
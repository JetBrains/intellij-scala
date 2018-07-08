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

trait ScEnumerator extends ScPatterned {
  def rvalue: ScExpression

  def valKeyword: Option[PsiElement] = {
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)
  }

  override def accept(visitor: ScalaElementVisitor): Unit = visitor.visitEnumerator(this)
}
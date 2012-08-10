package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import com.intellij.psi.PsiElement
import lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScEnumerator extends ScalaPsiElement with ScPatterned {
  def rvalue: ScExpression

  def valKeyword: Option[PsiElement] = {
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)
  }

  override def accept(visitor: ScalaElementVisitor) = visitor.visitEnumerator(this)
}
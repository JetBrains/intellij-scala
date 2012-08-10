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
* Date: 07.03.2008
*/

trait ScGenerator extends ScalaPsiElement with ScPatterned{
  def guard: ScGuard

  def rvalue: ScExpression

  def valKeyword: Option[PsiElement] = {
    Option(getNode.findChildByType(ScalaTokenTypes.kVAL)).map(_.getPsi)
  }

  override def accept(visitor: ScalaElementVisitor) = visitor.visitGenerator(this)
}
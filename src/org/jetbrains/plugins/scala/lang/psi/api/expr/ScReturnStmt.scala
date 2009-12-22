package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReturnStmt extends ScExpression {
  def expr = findChild(classOf[ScExpression])

  def returnKeyword: PsiElement

  override def accept(visitor: ScalaElementVisitor) = visitor.visitReturnStatement(this)
}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import statements.ScFunctionDefinition

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScReturnStmt extends ScExpression {
  def expr = findChild(classOf[ScExpression])

  def returnKeyword: PsiElement

  def returnFunction: Option[ScFunctionDefinition]

  override def accept(visitor: ScalaElementVisitor) = visitor.visitReturnStatement(this)
}
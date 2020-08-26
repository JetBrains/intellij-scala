package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements


import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScVariableDefinition extends ScVariable {
  def pList: ScPatternList

  def bindings: collection.Seq[ScBindingPattern]

  override def declaredElements: collection.Seq[ScBindingPattern] = bindings

  def assignment: Option[PsiElement]

  def expr: Option[ScExpression]

  def isSimple: Boolean = pList.simplePatterns && bindings.size == 1

  override def isAbstract: Boolean = false

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitVariableDefinition(this)
  }
}

object ScVariableDefinition {
  object expr {
    def unapply(definition: ScVariableDefinition): Option[ScExpression] = Option(definition).flatMap(_.expr)
  }
}

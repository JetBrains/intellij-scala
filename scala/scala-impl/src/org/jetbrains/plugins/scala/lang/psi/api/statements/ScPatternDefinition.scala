package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */

trait ScPatternDefinition extends ScValue with ScValueOrVariableDefinition {

  override def isSimple: Boolean = pList.simplePatterns && bindings.size == 1

  override def isAbstract: Boolean = false

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPatternDefinition(this)
  }

  def transparentExport: Option[PsiNamedElement]
}

object ScPatternDefinition {
  object expr {
    def unapply(definition: ScPatternDefinition): Option[ScExpression] = definition.expr
  }
}

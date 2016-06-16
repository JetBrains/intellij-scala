package org.jetbrains.plugins.scala.lang.transformation
package declarations

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
object ExpandProcedureSyntax extends AbstractTransformer {
  def transformation = {
    case (e: ScFunctionDefinition) if !e.hasAssign =>
      val prototype = code"def f(): Unit = ()".asInstanceOf[ScFunctionDefinition]
      val colon = prototype.getParameterList.getNextSibling
      val equals = prototype.assignment.get
      e.addRangeAfter(colon, equals, e.getParameterList)
  }
}

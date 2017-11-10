package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes

object ShapelessApplyWitness extends ScalaMacroTypeable with ShapelessUtils {

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (macros.isParameterless) None
    else {
      val name = context.place match {
        case ScLiteral(value: Symbol) => value.name
        case _ => "UNRESOLVED1488"
      }
      ScalaPsiElementFactory
        .createTypeFromText(s"Symbol => Witness.Aux[Symbol with tag.Tagged[{type $name}]]", context.place, null)
    }
  }
}

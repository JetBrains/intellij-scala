package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

object ShapelessMaterializeGeneric extends ScalaMacroTypeable with ShapelessUtils {

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None
    val targetClass = extractTargetClass(context)
    val fields      = targetClass.flatMap(_.constructor.map(_.parameters)).getOrElse(Seq.empty)
    val reprTpStr   = fields.foldRight(fqHNil)((p, suffix) =>
      s"$fqColonColon[${p.`type`().getOrAny.presentableText}, $suffix]"
    )
    val genericStr  = s"$fqGeneric[${targetClass.map(_.name).getOrElse("Any")}]"
    ScalaPsiElementFactory.createTypeFromText(s"$genericStr{type Repr = $reprTpStr}", context.place, null)
  }
}
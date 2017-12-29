package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * generates HList types without tagging
  *
  * example:
  * case class Foo(age: Int, name: String) ->
  * -> Generic.Aux[Main.this.Foo, shapeless.this.::[Int, shapeless.this.::[String, shapeless.this.HNil]]]
  */
object ShapelessMaterializeGeneric extends ScalaMacroTypeable with ShapelessUtils {

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    if (context.expectedType.isEmpty) return None
    val targetType = extractTargetType(context)
    val fields      = extractFields(targetType).map(_._2)
    val reprTpStr   = hlistText(fields)
    val genericStr  = s"$fqGeneric.Aux[${targetType.canonicalText}, $reprTpStr]"
    ScalaPsiElementFactory.createTypeFromText(genericStr, context.place, null)
  }
}
package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Nikolay.Tropin
  * 15-Jan-18
  */
object ShapelessWitnessSelectDynamic extends ScalaMacroTypeable with ShapelessUtils {

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] = {
    val ref = context.place match {
      case r: ScStableCodeReferenceElement => r
      case _ => return None
    }
    ref.refName match {
      case ScalaNamesUtil.isBacktickedName(literalText) =>
        val literal = createExpressionWithContextFromText(literalText, ref.getContext, ref) match {
          case _: ScInterpolatedStringLiteral => return None
          case lit: ScLiteral => lit
          case _ => return None
        }
        //todo: replace with singleton literal type when supported
        val typeOfLiteral = literal.`type`().getOrAny.canonicalText
        val typeCarrierText =
          s"""
             |object `$literalText` {
             |  type T = $typeOfLiteral
             |  type Field[V] = $fqFieldType[$typeOfLiteral, V]
             |  type ->>[V] = Field[V]
             |}
          """.stripMargin
        val tempObject = createObjectWithContext(typeCarrierText, macros.getContext, macros)

        tempObject.`type`().toOption
      case _ => None
    }
  }
}

package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import com.intellij.psi.PsiElement
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

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] =
    backtickedLiteralIn(context.place)
      .flatMap(typeCarrierType(_, macros))

  private def backtickedLiteralIn(e: PsiElement): Option[ScLiteral] = {
    val ref = e match {
      case r: ScStableCodeReferenceElement => r
      case _ => return None
    }
    ref.refName match {
      case ScalaNamesUtil.isBacktickedName(literalText) =>
        createExpressionWithContextFromText(literalText, ref.getContext, ref) match {
          case _: ScInterpolatedStringLiteral => None
          case lit: ScLiteral => Some(lit)
          case _ => None
        }
      case _ => None
    }
  }

  private def typeCarrierType(literal: ScLiteral, insertionPlace: PsiElement): Option[ScType] ={
    //todo: replace with singleton literal type when supported
    val literalType = literal.`type`().getOrAny.canonicalText
    val text = s"""
                  |object `${literal.getText}` {
                  |  type T = $literalType
                  |  type Field[V] = $fqFieldType[$literalType, V]
                  |  type ->>[V] = Field[V]
                  |}""".stripMargin
    val typeCarrier = createObjectWithContext(text, insertionPlace.getContext, insertionPlace)
    typeCarrier.`type`().toOption
  }
}

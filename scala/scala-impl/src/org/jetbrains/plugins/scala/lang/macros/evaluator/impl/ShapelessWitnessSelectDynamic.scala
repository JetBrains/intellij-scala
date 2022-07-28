package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

object ShapelessWitnessSelectDynamic extends ScalaMacroTypeable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("selectDynamic", "shapeless.Witness") :: Nil

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] =
    backtickedLiteralIn(context.place)
      .flatMap(typeCarrierType(_, macros))

  private def backtickedLiteralIn(e: PsiElement): Option[String] = {
    val ref = e match {
      case r: ScStableCodeReference => r
      case _ => return None
    }
    ref.refName match {
      case ScalaNamesUtil.isBacktickedName(literalText) =>
        createExpressionWithContextFromText(literalText, ref.getContext, ref).getNonValueType() match {
          case Right(ScLiteralType(value, _)) => Some(value.presentation)
          case _ => None
        }
      case _ => None
    }
  }

  private def typeCarrierType(literalText: String, insertionPlace: PsiElement): Option[ScType] ={
    val text = s"""
                  |object `$literalText` {
                  |  type T = $literalText
                  |  type Field[V] = $fqFieldType[$literalText, V]
                  |  type ->>[V] = Field[V]
                  |}""".stripMargin
    val typeCarrier = createObjectWithContext(text, insertionPlace.getContext, insertionPlace)
    typeCarrier.`type`().toOption
  }
}

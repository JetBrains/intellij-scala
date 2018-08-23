package org.jetbrains.plugins.scala.lang.macros.evaluator.impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, MacroImpl, ScalaMacroTypeable}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Nikolay.Tropin
  * 15-Jan-18
  */
object ShapelessWitnessSelectDynamic extends ScalaMacroTypeable with ShapelessUtils {

  override val boundMacro: Seq[MacroImpl] = MacroImpl("selectDynamic", "shapeless.Witness") :: Nil

  override def checkMacro(macros: ScFunction, context: MacroContext): Option[ScType] =
    backtickedLiteralIn(context.place)
      .flatMap(typeCarrierType(_, macros))

  private def backtickedLiteralIn(e: PsiElement): Option[String] = {
    val ref = e match {
      case r: ScStableCodeReferenceElement => r
      case _ => return None
    }
    ref.refName match {
      case ScalaNamesUtil.isBacktickedName(literalText) =>
        val hasMinus = literalText.startsWith("-")
        val cleanedText = literalText.stripPrefix("-")
        createExpressionWithContextFromText(cleanedText, ref.getContext, ref) match {
          case _: ScInterpolatedStringLiteral => None
          case lit: ScLiteral =>
            ScLiteralType.kind(lit.getFirstChild.getNode, lit)
              .flatMap { kind =>
                if (!hasMinus || ScLiteralType.isNumeric(kind)) Some(literalText)
                else None
              }
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

    ScLiteralImpl.markMacroGenerated(typeCarrier)

    typeCarrier.`type`().toOption
  }
}

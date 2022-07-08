package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.util._

/**
  * Macro for suggesting name.
  */
final class SuggestScalaVariableNameMacro extends ScalaMacro {

  import SuggestScalaVariableNameMacro._

  override def calculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] =
    getNames(params)(context) match {
      case names if names.length < 2 => null
      case names =>
        names.map { s =>
          LookupElementBuilder.create(s, s)
        }.toArray
    }

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    getNames(params)(context)
      .map(new TextResult(_))
      .headOption
      .orNull

  override def getPresentableName: String = CodeInsightBundle.message("macro.suggest.variable.name")

  override def getDefaultValue: String = "value"

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = null
}

object SuggestScalaVariableNameMacro {

  private def getNames(params: Array[Expression])
                      (implicit context: ExpressionContext): Seq[String] = {
    val editor = context.getEditor
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)

    temp(params.map(ScalaVariableOfTypeMacro.calculate)) match {
      case Some(scType) => NameSuggester.suggestNamesByType(scType)
      case None => Seq("x")
    }
  }

  private def temp(expressions: Array[String])
                  (implicit context: ExpressionContext): Option[ScType] = expressions match {
    case Array(first@("option" | "foreach"), second, _*) =>
      val tried = Try {
        val str = first match {
          case "option" => "scala.Option"
          case "foreach" => "foreach"
        }
        (new ScalaVariableOfTypeMacro.RegularVariable)
          .calculateLookups(Array(str), showOne = true)
          .map(_.getObject)
          .collectFirst {
            case typed: ScTypedDefinition if typed.name == second => typed
          }.flatMap(_.`type`().toOption)
          .collect {
            case ParameterizedType(_, typeArgs) => typeArgs.head
            case JavaArrayType(argument) => argument
          }
      }

      tried match {
        case Success(Some(scType)) => Some(scType)
        case _ => None
      }
    case _ => None
  }

}

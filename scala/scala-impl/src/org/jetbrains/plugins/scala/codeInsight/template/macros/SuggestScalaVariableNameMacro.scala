package org.jetbrains.plugins.scala
package codeInsight.template.macros

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.psi.{PsiDocumentManager, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
  * User: Alexander Podkhalyuzin
  * Date: 31.01.2009
  */

/**
  * Macro for suggesting name.
  */
class SuggestScalaVariableNameMacro extends ScalaMacro {

  import SuggestScalaVariableNameMacro._

  override def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    val names = getNames(params, context).toArray
    if (names.length < 2) return null
    names.map(s => LookupElementBuilder.create(s, s))
  }

  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext): Result =
    getNames(params, context)
      .map(new TextResult(_))
      .headOption
      .orNull

  def getDescription: String = "Macro for suggesting name"

  def getName: String = "suggestScalaVariableName"

  override def getDefaultValue: String = "value"

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = null

  def getPresentableName: String = "Suggest Scala variable macro"
}

object SuggestScalaVariableNameMacro {
  private def getNames(params: Array[Expression], context: ExpressionContext): Seq[String] = {
    val p: Array[String] = params.map(_.calculateResult(context).toString)
    val editor = context.getEditor
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)

    val default = Seq("x")
    val typez: ScType = p match {
      case Array() => return default //todo:
      case x if x(0) == "option" || x(0) == "foreach" =>
        try {
          val items = (new ScalaVariableOfTypeMacro).calculateLookupItems(Array[String](x(0) match {
            case "option" => "scala.Option"
            case "foreach" => "foreach"
          }), context, showOne = true).
            map(_.getObject).filter(_.isInstanceOf[PsiNamedElement]).map(_.asInstanceOf[PsiNamedElement]).
            filter(_.name == x(1))
          if (items.length == 0) return default
          items(0) match {
            case typed: ScTypedDefinition => typed.getType() match {
              case Success(ParameterizedType(_, typeArgs), _) => typeArgs.head
              case Success(JavaArrayType(argument), _) => argument
              case _ => return default
            }
            case _ => return default
          }
        }
        catch {
          case e: Exception =>
            e.printStackTrace()
            return default
        }
      case _ => return default
    }
    NameSuggester.suggestNamesByType(typez)
  }
}

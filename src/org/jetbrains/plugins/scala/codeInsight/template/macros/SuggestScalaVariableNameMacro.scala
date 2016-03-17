package org.jetbrains.plugins.scala
package codeInsight.template.macros

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.psi.{PsiDocumentManager, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{JavaArrayType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.01.2009
 */

/**
 * Macro for suggesting name.
 */
class SuggestScalaVariableNameMacro extends ScalaMacro {
  override def innerCalculateLookupItems(params: Array[Expression], context: ExpressionContext)
                                        (implicit typeSystem: TypeSystem): Array[LookupElement] = {
    val a = SuggestNamesUtil.getNames(params, context)
    if (a.length < 2) return null
    a.map((s: String) => LookupElementBuilder.create(s, s))
  }

  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                   (implicit typeSystem: TypeSystem): Result = {
    val a = SuggestNamesUtil.getNames(params, context)
    if (a.length == 0) return null
    new TextResult(a(0))
  }

  def getDescription: String = "Macro for suggesting name"

  def getName: String = "suggestScalaVariableName"

  override def getDefaultValue: String = "value"

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = null

  def getPresentableName: String = "Suggest Scala variable macro"
}

object SuggestNamesUtil {
  def getNames(params: Array[Expression], context: ExpressionContext)
              (implicit typeSystem: TypeSystem): Array[String] = {
    val p: Array[String] = params.map(_.calculateResult(context).toString)
    val offset = context.getStartOffset
    val editor = context.getEditor
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
    val element = file.findElementAt(offset)
    val typez: ScType = p match {
      case x if x.length == 0 => return Array[String]("x") //todo:
      case x if x(0) == "option" || x(0) == "foreach" =>
        try {
          val items = (new ScalaVariableOfTypeMacro).calculateLookupItems(Array[String](x(0) match {
            case "option" => "scala.Option"
            case "foreach" => "foreach"
          }), context, showOne = true).
                  map(_.getObject).filter(_.isInstanceOf[PsiNamedElement]).map(_.asInstanceOf[PsiNamedElement]).
                  filter(_.name == x(1))
          if (items.length == 0) return Array[String]("x")
          items(0) match {
            case typed: ScTypedDefinition => typed.getType(TypingContext.empty) match {
              case Success(ScParameterizedType(_, typeArgs), _) => typeArgs.head
              case Success(JavaArrayType(arg), _) => arg
              case _ => return Array[String]("x")
            }
            case _ => return Array[String]("x")
          }
        }
        catch {
          case e: Exception =>
            e.printStackTrace()
            return Array[String]("x")
        }
      case _ => return Array[String]("x")
    }
    NameSuggester.suggestNamesByType(typez)
  }
}
package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDocumentManager}
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import _root_.scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

/**
 * This class provides macros for live templates. Return elements
 * of given class type (or class types).
 */
class ScalaVariableOfTypeMacro extends Macro {
  def getPresentableName: String = "Scala variable of type macro"

  override def calculateLookupItems(exprs: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    calculateLookupItems(exprs.map(_.calculateResult(context).toString), context, showOne = false)
  }
  def calculateLookupItems(exprs: Array[String], context: ExpressionContext, showOne: Boolean): Array[LookupElement] = {
    if (exprs.length == 0) return null
    val offset = context.getStartOffset
    val editor = context.getEditor
    val array = new ArrayBuffer[LookupElement]
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
    file match {
      case file: ScalaFile =>
        val element = file.findElementAt(offset)
        val variants = MacroUtil.getVariablesForScope(element).filter(r => {
          val clazz = PsiTreeUtil.getParentOfType(r.element, classOf[PsiClass])
          if (clazz == null) true
          else {
            clazz.qualifiedName match {
              case "scala.Predef" => false
              case "scala" => false
              case _ => true
            }
          }
        })
        for (variant <- variants) {
          variant.getElement match {
            case typed: ScTypedDefinition =>
              for (t <- typed.getType(TypingContext.empty))
              exprs.apply(0) match {
                case "" =>
                  val item = LookupElementBuilder.create(variant.getElement, variant.getElement.name)
                  item.setTypeText(ScType.presentableText(t))
                  array += item
                case "foreach" =>
                  ScType.extractClass(t) match {
                    case Some(x: ScTypeDefinition) =>
                      if (x.functionsByName("foreach").nonEmpty) {
                        array += LookupElementBuilder.create(variant.getElement, variant.getElement.name)
                      }
                    case _ =>
                  }
                case  _ =>
                  for (expr <- exprs) {
                    if ((ScType.extractClass(t) match {
                      case Some(x) => x.qualifiedName
                      case None => ""
                    }) == expr) array += LookupElementBuilder.create(variant.getElement, variant.getElement.name)
                  }
              }
            case _ =>
          }
        }
      case _ =>
    }
    if (array.length < 2 && !showOne) return null
    array.toArray
  }

  def calculateResult(exprs: Array[Expression], context: ExpressionContext): Result = {
    if (exprs.length == 0) return null
    val offset = context.getStartOffset
    val editor = context.getEditor
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    PsiDocumentManager.getInstance(editor.getProject).commitDocument(editor.getDocument)
    file match {
      case file: ScalaFile =>
        val element = file.findElementAt(offset)
        val variants = MacroUtil.getVariablesForScope(element).filter(r => {
          val clazz = PsiTreeUtil.getParentOfType(r.element, classOf[PsiClass])
          if (clazz == null) true
          else {
            clazz.qualifiedName match {
              case "scala.Predef" => false
              case "scala" => false
              case _ => true
            }
          }
        })
        for (variant <- variants) {
          variant.getElement match {
            case typed: ScTypedDefinition =>
              for (t <- typed.getType(TypingContext.empty))
              exprs.apply(0).calculateResult(context).toString match {
                case "" =>
                  return new TextResult(variant.getElement.name)
                case "foreach" =>
                  ScType.extractClassType(t, Some(file.getProject)) match {
                    case Some((x: ScTypeDefinition, _)) =>
                      if (x.functionsByName("foreach").nonEmpty) return new TextResult(variant.getElement.name)
                    case _ =>
                  }
                case _ =>
                  for (expr <- exprs) {
                    if ((ScType.extractClassType(t, Some(file.getProject)) match {
                      case Some((x, _)) => x.qualifiedName
                      case None => ""
                    }) == expr.calculateResult(context).toString) return new TextResult(variant.getElement.name)
                  }
              }
            case _ =>
          }
        }
        null
      case _ => null
    }
  }

  override def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def getDescription: String = CodeInsightBundle.message("macro.variable.of.type")

  def getName: String = "scalaVariableOfType"

  override def getDefaultValue: String = "x"

}
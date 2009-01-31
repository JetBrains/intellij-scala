package org.jetbrains.plugins.scala.codeInsight.template

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.codeInsight.template._

import com.intellij.psi.{PsiDocumentManager, PsiType}
import java.lang.String
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.ScTyped
import util.MacroUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

class ScalaVariableOfTypeMacro extends Macro {
  def calculateLookupItems(exprs: Array[Expression], context: ExpressionContext): Array[LookupElement] = {
    val offset = context.getStartOffset
    val editor = context.getEditor
    val array = new ArrayBuffer[LookupElement]
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    file match {
      case file: ScalaFile => {
        val element = file.findElementAt(offset)
        val variants = MacroUtil.getVariablesForScope(element)
        for (variant <- variants) {
          variant.getElement match {
            case typed: ScTyped => {
              val t = typed.calcType
              for (expr <- exprs) {
                if ((ScType.extractClassType(t) match {
                  case Some((x, _)) => x.getQualifiedName
                  case None => ""
                }) == expr.calculateResult(context).toString) array +=  new LookupItem(variant.getElement, variant.getElement.getName)
              }
            }
            case _ =>
          }
        }
      }
      case _ =>
    }
    array.toArray
  }

  def calculateResult(exprs: Array[Expression], context: ExpressionContext): Result = {
    val offset = context.getStartOffset
    val editor = context.getEditor
    val file = PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
    file match {
      case file: ScalaFile => {
        val element = file.findElementAt(offset)
        val variants = MacroUtil.getVariablesForScope(element)
        for (variant <- variants) {
          variant.getElement match {
            case typed: ScTyped => {
              val t = typed.calcType
              for (expr <- exprs) {
                if ((ScType.extractClassType(t) match {
                  case Some((x, _)) => x.getQualifiedName
                  case None => ""
                }) == expr.calculateResult(context).toString) return new TextResult(variant.getElement.getName)
              }
            }
            case _ =>
          }
        }
        return null
      }
      case _ => null
    }
  }

  def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def getDescription: String = CodeInsightBundle.message("macro.variable.of.type")

  def getName: String = "scalaVariableOfType"

  def getDefaultValue: String = "x"

}
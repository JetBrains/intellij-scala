package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.codeInsight.template._
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiDocumentManager}
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectExt

import _root_.scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

/**
 * This class provides macros for live templates. Return elements
 * of given class type (or class types).
 */
class ScalaVariableOfTypeMacro extends ScalaMacro {
  def getPresentableName: String = "Scala variable of type macro"

  override def innerCalculateLookupItems(exprs: Array[Expression], context: ExpressionContext)
                                        (implicit typeSystem: TypeSystem): Array[LookupElement] = {
    calculateLookupItems(exprs.map(_.calculateResult(context).toString), context, showOne = false)
  }

  def calculateLookupItems(exprs: Array[String], context: ExpressionContext, showOne: Boolean)
                          (implicit typeSystem: TypeSystem): Array[LookupElement] = {
    if (!validExprs(exprs)) return null
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
                addLookupItems(exprs, context, variant, t, file.getProject, array)
            case _ =>
          }
        }
      case _ =>
    }
    if (array.length < 2 && !showOne) return null
    array.toArray
  }

  def innerCalculateResult(exprs: Array[Expression], context: ExpressionContext)
                          (implicit typeSystem: TypeSystem): Result = {
    if (!validExprs(exprs)) return null
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
                getResult(exprs, context, variant, t, file.getProject).map(return _)
            case _ =>
          }
        }
        null
      case _ => null
    }
  }

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]

  override def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def getDescription: String = CodeInsightBundle.message("macro.variable.of.type")

  def getName: String = "scalaVariableOfType"

  override def getDefaultValue: String = "x"

  def validExprs(exprs: Array[Expression]): Boolean = validExprsCount(exprs.length)

  def validExprs(exprs: Array[String]): Boolean = validExprsCount(exprs.length)

  def validExprsCount(exprsCount: Int): Boolean = exprsCount != 0

  def getResult(exprs: Array[Expression],
                context: ExpressionContext,
                variant: ScalaResolveResult,
                scType: ScType,
                project: Project)
               (implicit typeSystem: TypeSystem = project.typeSystem): Option[Result] = {
    exprs.apply(0).calculateResult(context).toString match {
      case "" =>
        Some(new TextResult(variant.getElement.name))
      case ScalaVariableOfTypeMacro.iterableId =>
        if (scType.canonicalText.startsWith("_root_.scala.Array")) Some(new TextResult(variant.getElement.name))
        else scType.extractClassType(project) match {
          case Some((x: ScTypeDefinition, _)) if x.functionsByName("foreach").nonEmpty =>
             Some(new TextResult(variant.getElement.name))
          case _ =>
            None
        }
      case _ =>
        exprs.find(expr => (scType.extractClassType(project) match {
          case Some((x, _)) => x.qualifiedName
          case None => ""
        }) == expr.calculateResult(context).toString).map(_ => new TextResult(variant.getElement.name))
    }
  }

  def addLookupItems(exprs: Array[String],
                     context: ExpressionContext,
                     variant: ScalaResolveResult,
                     scType: ScType,
                     project: Project,
                     array: ArrayBuffer[LookupElement])
                    (implicit typeSystem: TypeSystem = project.typeSystem) {
    exprs.apply(0) match {
      case "" =>
        val item = LookupElementBuilder.create(variant.getElement, variant.getElement.name).
          withTypeText(scType.presentableText)
        array += item
      case ScalaVariableOfTypeMacro.iterableId if scType.canonicalText.startsWith("_root_.scala.Array") =>
        array += LookupElementBuilder.create(variant.getElement, variant.getElement.name)
      case ScalaVariableOfTypeMacro.iterableId =>
        scType.extractClass() match {
          case Some(x: ScTypeDefinition) if x.functionsByName("foreach").nonEmpty =>
              array += LookupElementBuilder.create(variant.getElement, variant.getElement.name)
          case _ =>
        }
      case  _ =>
        for (expr <- exprs) {
          if ((scType.extractClass() match {
            case Some(x) => x.qualifiedName
            case None => ""
          }) == expr) array += LookupElementBuilder.create(variant.getElement, variant.getElement.name)
        }
    }
  }
}

object ScalaVariableOfTypeMacro {
  val iterableId = "foreach"
}
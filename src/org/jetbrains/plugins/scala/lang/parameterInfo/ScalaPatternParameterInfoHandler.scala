package org.jetbrains.plugins.scala.lang.parameterInfo


import _root_.java.lang.{Class, String}
import collection.mutable.{ArrayBuffer, HashSet}
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.{PsiParameter, PsiMember, PsiElement, PsiMethod}
import psi.api.statements.params.{ScParameterClause, ScParameter}
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.psi.tree.IElementType
import _root_.java.util.Set
import com.intellij.lang.parameterInfo._



import com.intellij.psi.util.PsiTreeUtil
import psi.api.base.{ScPrimaryConstructor, ScStableCodeReferenceElement, ScConstructor}
import psi.api.expr.{ScGenericCall, ScArgumentExprList, ScMethodCall, ScExpression}

import psi.api.statements.ScFunction
import psi.api.toplevel.typedef.{ScObject, ScClass}

import psi.ScalaPsiUtil
import psi.types.{ScType, ScSubstitutor, PhysicalSignature}
import scala.editor.documentationProvider.ScalaDocumentationProvider
import resolve.{ResolveUtils, ScalaResolveResult}
import com.intellij.util.ArrayUtil
import java.awt.Color
import lexer.ScalaTokenTypes
import psi.api.base.patterns.{ScPattern, ScConstructorPattern, ScPatternArgumentList}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class ScalaPatternParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScPatternArgumentList, Any, ScPattern] {
  def getParameterCloseChars: String = "{},);\n"

  def couldShowInLookup: Boolean = true

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(patternArgumentList: ScPatternArgumentList): Array[ScPattern] = patternArgumentList.patterns.toArray

  def getArgumentListClass: Class[ScPatternArgumentList] = classOf[ScPatternArgumentList]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: Set[Class[_]] = {
    val set = new java.util.HashSet[Class[_]]()
    set.add(classOf[ScConstructorPattern])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScPatternArgumentList = {
    findCall(context)
  }

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = ArrayUtil.EMPTY_OBJECT_ARRAY

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = null


  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
     if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case args: ScPatternArgumentList => {
        var color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        p match { //todo: join this match statement with same in FunctionParameterHandler to fix code duplicate.
          case x: String if x == "" => {
            buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
          }
          case (sign: PhysicalSignature, i: Int) => { //i  can be -1 (it's update method)
            val subst = sign.substitutor
            sign.method match {
              case method: ScFunction => {
                val clauses = method.paramClauses.clauses
                if (clauses.length <= i || (i == -1 && clauses.length == 0)) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  val clause: ScParameterClause = if (i >= 0) clauses(i) else clauses(0)
                  val length = clause.parameters.length
                  val parameters = if (i != -1) clause.parameters else clause.parameters.subseq(0, length - 1)
                  if (parameters.length > 0) {
                    if (clause.isImplicit) buffer.append("implicit ")
                    buffer.append(parameters.
                            map((param: ScParameter) => {
                      val isBold = if (parameters.indexOf(param) == index || (param.isRepeatedParameter && index >= parameters.indexOf(param))) true
                      else {
                        //todo: check type
                        false
                      }
                      val paramText = ScalaDocumentationProvider.parseParameter(param, (t: ScType) => ScType.presentableText(subst.subst(t)))
                      if (isBold) "<b>" + paramText + "</b>" else paramText
                    }).mkString(", "))
                  } else buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                }
              }
              case method: PsiMethod => {
                val p = method.getParameterList
                if (p.getParameters.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
                else {
                  buffer.append(p.getParameters.
                          map((param: PsiParameter) => {
                    val buffer: StringBuilder = new StringBuilder("")
                    val list = param.getModifierList
                    if (list == null) return;
                    val lastSize = buffer.length
                    for (a <- list.getAnnotations) {
                      if (lastSize != buffer.length) buffer.append(" ")
                      val element = a.getNameReferenceElement();
                      if (element != null) buffer.append("@").append(element.getText)
                    }
                    if (lastSize != buffer.length) buffer.append(" ")
                    val paramType = param.getType

                    val name = param.getName
                    if (name != null) {
                      buffer.append(name)
                    }
                    buffer.append(": ")
                    buffer.append(ScType.presentableText(subst.subst(ScType.create(paramType, method.getProject))))

                    val isBold = if (p.getParameters.indexOf(param) == index || (param.isVarArgs && p.getParameters.indexOf(param) <= index)) true
                    else {
                      //todo: check type
                      false
                    }
                    val paramText = buffer.toString
                    if (isBold) "<b>" + paramText + "</b>" else paramText
                  }).mkString(", "))
                }
              }
            }
          }
          case (constr: ScPrimaryConstructor, subst: ScSubstitutor, i: Int) => {
            val clauses = constr.parameterList.clauses
            if (clauses.length <= i) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
            else {
              val clause: ScParameterClause = clauses(i)
              if (clause.parameters.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
              else {
                if (clause.isImplicit) buffer.append("implicit ")
                buffer.append(clause.parameters.
                        map((param: ScParameter) => {
                  val isBold = if (clause.parameters.indexOf(param) == index) true
                  else {
                    //todo: check type
                    false
                  }
                  val paramText = ScalaDocumentationProvider.parseParameter(param, (t: ScType) => ScType.presentableText(subst.subst(t)))
                  if (isBold) "<b>" + paramText + "</b>" else paramText
                }).mkString(", "))
              }
            }
          }
          case _ =>
        }
        val isGrey = buffer.indexOf("<g>")
        if (isGrey != -1) buffer.replace(isGrey, isGrey + 3, "")
        val startOffset = buffer.indexOf("<b>")
        if (startOffset != -1) buffer.replace(startOffset, startOffset + 3, "")

        val endOffset = buffer.indexOf("</b>")
        if (endOffset != -1) buffer.replace(endOffset, endOffset + 4, "")

        if (buffer.toString != "")
          context.setupUIComponentPresentation(buffer.toString, startOffset, endOffset, false, false, false, color)
        else
          context.setUIComponentEnabled(false)
      }
      case _ =>
    }
  }

  def showParameterInfo(element: ScPatternArgumentList, context: CreateParameterInfoContext): Unit = {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def updateParameterInfo(o: ScPatternArgumentList, context: UpdateParameterInfoContext): Unit = {
    if (context.getParameterOwner != o) context.removeHint
    val offset = context.getOffset
    var child = o.getNode.getFirstChildNode
    var i = 0
    while (child != null && child.getStartOffset < offset) {
      if (child.getElementType == ScalaTokenTypes.tCOMMA) i = i + 1
      child = child.getTreeNext
    }
    context.setCurrentParameter(i)
  }

  def tracksParameterIndex: Boolean = true

  private def findCall(context: ParameterInfoContext): ScPatternArgumentList = { //todo: Expected type
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args: ScPatternArgumentList = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext => {
          args.getParent match {
            case constr: ScConstructorPattern => {
              val ref: ScStableCodeReferenceElement = constr.ref
              val res: ArrayBuffer[Object] = new ArrayBuffer[Object]
              if (ref != null) {
                val name = ref.refName
                val variants: Array[Object] = ref.getSameNameVariants
                for (variant <- variants if !variant.isInstanceOf[PsiMember] ||
                        ResolveUtils.isAccessible(variant.asInstanceOf[PsiMember], ref)) {
                  variant match {
                    case obj: ScObject => {
                      //unapply method
                      for (n <- ScalaPsiUtil.getUnapplyMethods(obj)) {
                        res += (n, 0)
                      }
                    }
                    case clazz: ScClass if clazz.isCase => {
                      clazz.constructor match {
                        case Some(constr: ScPrimaryConstructor) => {
                          res += (constr, ScSubstitutor.empty, 0)
                        }
                        case None => res += ""
                      }
                    }
                    case _ =>
                  }
                }
              }
              context.setItemsToShow(res.toArray)
            }
            case _ =>
          }
        }
        case context: UpdateParameterInfoContext => {
          var el = element
          while (el.getParent != args) el = el.getParent
          var index = 1
          for (pattern <- args.patterns if pattern != el) index += 1
          context.setCurrentParameter(index)
          context.setHighlightedParameter(el)
        }
        case _ =>
      }

    }
    return args
  }
}
package org.jetbrains.plugins.scala
package lang
package parameterInfo


import java.awt.Color

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */
class ScalaTypeParameterInfoHandler extends ParameterInfoHandlerWithTabActionSupport[ScTypeArgs, Any, ScTypeElement] {
  def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod]) //todo: ?
  }

  def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  def getActualParameters(o: ScTypeArgs): Array[ScTypeElement] = o.typeArgs.toArray

  def getArgumentListClass: Class[ScTypeArgs] = classOf[ScTypeArgs]

  def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new java.util.HashSet[Class[_]]()
    set.add(classOf[ScParameterizedTypeElement])
    set.add(classOf[ScGenericCall])
    set
  }

  def findElementForParameterInfo(context: CreateParameterInfoContext): ScTypeArgs = {
    findCall(context)
  }

  def getParameterCloseChars: String = "{},];\n"

  def getParametersForDocumentation(p: Any, context: ParameterInfoContext): Array[Object] = ArrayUtil.EMPTY_OBJECT_ARRAY

  def findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ScTypeArgs = {
    findCall(context)
  }

  def couldShowInLookup: Boolean = true

  def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    context.getParameterOwner match {
      case _: ScTypeArgs =>
        val color: Color = context.getDefaultParameterColor
        val index = context.getCurrentParameterIndex
        val buffer: StringBuilder = new StringBuilder("")
        p match {
          case (owner: ScTypeParametersOwner, substitutor: ScSubstitutor) =>
            val params = owner.typeParameters
            appendScTypeParams(params, buffer, index, substitutor)
          case (method: PsiMethod, substitutor: ScSubstitutor) =>
            val params = method.getTypeParameters
            appendPsiTypeParams(params, buffer, index, substitutor)
          case (clazz: PsiClass, substitutor: ScSubstitutor) =>
            clazz match {
              case td: ScTypeDefinition =>
                val params: Seq[ScTypeParam] = td.typeParameters
                appendScTypeParams(params, buffer, index, substitutor)
              case _ =>
                val params = clazz.getTypeParameters
                appendPsiTypeParams(params, buffer, index, substitutor)
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
      case _ =>
    }
  }

  private def appendPsiTypeParams(params: Array[PsiTypeParameter], buffer: scala.StringBuilder, index: Int, substitutor: ScSubstitutor) {
    if (params.length == 0) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
    else {
      buffer.append(params.map((param: PsiTypeParameter) => {
        val isBold = if (params.indexOf(param) == index) true
        else {
          //todo: check type
          false
        }
        var paramText = param.name
        if (paramText == "?") paramText = "_"
        val refTypes = param.getExtendsList.getReferencedTypes
        if (refTypes.nonEmpty) {
          paramText = paramText + refTypes.map((typez: PsiType) => {
            substitutor.subst(typez.toScType()(param.projectContext)).presentableText
          }).mkString(" <: ", " with ", "")
        }
        if (isBold) "<b>" + paramText + "</b>" else paramText
      }).mkString(", "))
    }
  }

  private def appendScTypeParams(params: scala.Seq[ScTypeParam], buffer: StringBuilder, index: Int, substitutor: ScSubstitutor): StringBuilder = {
    if (params.isEmpty) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
    else {
      buffer.append(params.map((param: ScTypeParam) => {
        val isBold = if (params.indexOf(param) == index) true
        else {
          //todo: check type
          false
        }
        val paramText = StringBuilder.newBuilder ++= param.name

        def appendPresentableText(prefix: String, tp: ScType): Unit =
          paramText.append(prefix).append(substitutor.subst(tp).presentableText)

        def makeBold(): Unit = paramText.insert(0, "<b>").append("</b>")

        if (param.isContravariant) paramText.insert(0, "-")
        else if (param.isCovariant) paramText.insert(0, "+")

        val stdTypes = param.projectContext.stdTypes
        import stdTypes.{Any, Nothing}

        param.lowerBound foreach {
          case Nothing =>
          case tp: ScType => appendPresentableText(" >: ", tp)
        }
        param.upperBound foreach {
          case Any =>
          case tp: ScType => appendPresentableText(" <: ", tp)
        }
        param.viewBound foreach {
          (tp: ScType) => appendPresentableText(" <% ", tp)
        }
        param.contextBound foreach {
          (tp: ScType) => appendPresentableText(" : ", tp)
        }

        if (isBold) makeBold()

        paramText.mkString
      }).mkString(", "))
    }
  }

  def showParameterInfo(element: ScTypeArgs, context: CreateParameterInfoContext): Unit = {
    context.showHint(element, element.getTextRange.getStartOffset, this)
  }

  def getParametersForLookup(item: LookupElement, context: ParameterInfoContext): Array[Object] = null

  def updateParameterInfo(o: ScTypeArgs, context: UpdateParameterInfoContext): Unit = {
    //todo: join all this methods in all handlers to remove duplicates
    if (context.getParameterOwner != o) context.removeHint()
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

  private def fromResolved(ref: ScReferenceElement, useActualElement: Boolean = false): Option[(PsiElement, ScSubstitutor)] = {
    ref.bind() match {
      case Some(r @ ScalaResolveResult(m: PsiMethod, substitutor)) =>
        val element = if (useActualElement) r.getActualElement else m
        Some((element, substitutor))
      case Some(ScalaResolveResult(element @ (_: PsiClass | _: ScTypeParametersOwner), substitutor)) =>
        Some((element, substitutor))
      case Some(srr) =>
        srr.innerResolveResult.map(x => (x.getActualElement, x.substitutor))
      case _ => None
    }
  }

  private def findCall(context: ParameterInfoContext): ScTypeArgs = {
    val (file, offset) = (context.getFile, context.getOffset)
    val element = file.findElementAt(offset)
    if (element == null) return null
    val args: ScTypeArgs = PsiTreeUtil.getParentOfType(element, getArgumentListClass)
    if (args != null) {
      context match {
        case context: CreateParameterInfoContext =>
          val res = args.getParent match {
            case ScGenericCall(expr, _) => fromResolved(expr)
            case ScInfixExpr(_, ref, _) => fromResolved(ref)
            case ScParameterizedTypeElement(typeElem, _) =>
              typeElem match {
                case pt: ScTypeProjection => fromResolved(pt, useActualElement = true)
                case ScSimpleTypeElement(Some(ref)) => fromResolved(ref, useActualElement = true)
                case _ => None
              }
            case _: ScMacroDefinition => None//todo:
          }
          context.setItemsToShow(res.toArray)
        case context: UpdateParameterInfoContext =>
          var el = element
          while (el.getParent != args) el = el.getParent
          var index = 1
          for (typeElem <- args.typeArgs if typeElem != el) index += 1
          context.setCurrentParameter(index)
          context.setHighlightedParameter(el)
        case _ =>
      }
    }
    args
  }
}
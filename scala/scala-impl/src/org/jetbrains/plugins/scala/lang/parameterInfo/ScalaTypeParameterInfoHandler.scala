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
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */
class ScalaTypeParameterInfoHandler extends ScalaParameterInfoHandler[ScTypeArgs, Any, ScTypeElement] {
  override def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod]) //todo: ?
  }

  override def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  override def getActualParameters(o: ScTypeArgs): Array[ScTypeElement] = o.typeArgs.toArray

  override def getArgumentListClass: Class[ScTypeArgs] = classOf[ScTypeArgs]

  override def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  override def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new java.util.HashSet[Class[_]]()
    set.add(classOf[ScParameterizedTypeElement])
    set.add(classOf[ScGenericCall])
    set
  }

  override def couldShowInLookup: Boolean = true

  override def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    if (context == null || context.getParameterOwner == null || !context.getParameterOwner.isValid) return
    implicit val tpc: TypePresentationContext = TypePresentationContext(context.getParameterOwner)
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

  private def appendPsiTypeParams(params: Array[PsiTypeParameter], buffer: scala.StringBuilder, index: Int, substitutor: ScSubstitutor)(implicit tpc: TypePresentationContext): Unit = {
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
            substitutor(typez.toScType()(param.projectContext)).presentableText
          }).mkString(" <: ", tpc.compoundTypeSeparatorText, "")
        }
        if (isBold) "<b>" + paramText + "</b>" else paramText
      }).mkString(", "))
    }
  }

  private def appendScTypeParams(params: scala.Seq[ScTypeParam], buffer: StringBuilder, index: Int, substitutor: ScSubstitutor)(implicit tpc: TypePresentationContext): StringBuilder = {
    if (params.isEmpty) buffer.append(CodeInsightBundle.message("parameter.info.no.parameters"))
    else {
      buffer.append(params.map((param: ScTypeParam) => {
        val isBold = if (params.indexOf(param) == index) true
        else {
          //todo: check type
          false
        }
        val paramText = new StringBuilder() ++= param.name

        def appendPresentableText(prefix: String, tp: ScType): Unit =
          paramText.append(prefix).append(substitutor(tp).presentableText)

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

  private def fromResolved(ref: ScReference, useActualElement: Boolean = false): Option[(PsiElement, ScSubstitutor)] = {
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

  override protected def findCall(context: ParameterInfoContext): ScTypeArgs = {
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
            case ScParameterizedTypeElement(typeElement, _) =>
              val maybeReferenceElement = typeElement match {
                case projection: ScTypeProjection => Some(projection)
                case ScSimpleTypeElement(reference) => Some(reference)
                case _ => None
              }

              maybeReferenceElement.flatMap(fromResolved(_, useActualElement = true))
            case _ => None // todo: ScMacroDefinition
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
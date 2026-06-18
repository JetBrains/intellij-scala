package org.jetbrains.plugins.scala.lang.parameterInfo


import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.parameterInfo._
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScGenericCall, ScInfixExpr, ScParenthesisedExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, referenceTargetDeep}

import java.awt.Color

private object ScalaTypeParameterInfoHandler {
  private final case class ScTypeParameterClauseInfo(params: Seq[ScTypeParam], substitutor: ScSubstitutor)

  private final case class ResolvedElement(
    element:             PsiElement,
    substitutor:         ScSubstitutor,
    isExtensionCall:     Boolean,
    exportedInExtension: Option[ScExtension]
  ) {
    def toParameterInfo: (PsiElement, ScSubstitutor) = (element, substitutor)
  }
}

class ScalaTypeParameterInfoHandler extends ScalaParameterInfoHandler[ScTypeArgs, Any, ScTypeElement] {
  import ScalaTypeParameterInfoHandler.{ResolvedElement, ScTypeParameterClauseInfo}

  override def getArgListStopSearchClasses: java.util.Set[_ <: Class[_]] = {
    java.util.Collections.singleton(classOf[PsiMethod]) //todo: ?
  }

  override def getActualParameterDelimiterType: IElementType = ScalaTokenTypes.tCOMMA

  override def getActualParameters(o: ScTypeArgs): Array[ScTypeElement] =
    o.typeArguments.flatMap(_.typeElement).toArray

  override def getArgumentListClass: Class[ScTypeArgs] = classOf[ScTypeArgs]

  override def getActualParametersRBraceType: IElementType = ScalaTokenTypes.tRBRACE

  override def getArgumentListAllowedParentClasses: java.util.Set[Class[_]] = {
    val set = new java.util.HashSet[Class[_]]()
    set.add(classOf[ScParameterizedTypeElement])
    set.add(classOf[ScGenericCall])
    set
  }

  override def updateUI(p: Any, context: ParameterInfoUIContext): Unit = {
    Option(context)
      .flatMap(ctx => Option(ctx.getParameterOwner).filter(_.isValid).map(owner => (ctx, owner)))
      .foreach { case (ctx, owner) =>
        implicit val tpc: TypePresentationContext = TypePresentationContext(owner)
        owner match {
          case typeArgsOwner: ScTypeArgs =>
            val color: Color = ctx.getDefaultParameterColor
            val index = remapIndexForNamedTypeArgs(typeArgsOwner, ctx.getCurrentParameterIndex, p).getOrElse(-1)
            val buffer: StringBuilder = new StringBuilder("")
            p match {
              case ScTypeParameterClauseInfo(params, substitutor) =>
                appendScTypeParams(params, buffer, index, substitutor)
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
              ctx.setupUIComponentPresentation(buffer.toString, startOffset, endOffset, false, false, false, color)
            else
              ctx.setUIComponentEnabled(false)
          case _ =>
        }
      }
  }

  private def remapIndexForNamedTypeArgs(typeArgsOwner: ScTypeArgs, currentIndex: Int, parameterInfo: Any): Option[Int] = {
    val typeArgs                  = typeArgsOwner.typeArguments
    val currentArg                = typeArgs.lift(currentIndex)
    val hasNamedArgs              = typeArgs.exists(_.isNamed)
    val hasPositionalArgs         = typeArgs.exists(!_.isNamed)
    val isMixedNamedAndPositional = hasNamedArgs && hasPositionalArgs

    if (currentIndex < 0 || !hasNamedArgs) Option(currentIndex)
    else {
      val currentNamedArgName = currentArg.flatMap(_.name)

      if (isMixedNamedAndPositional && currentNamedArgName.isEmpty) None
      else {
        val maybeFormalParamNames = formalTypeParameterNames(parameterInfo)

        currentNamedArgName match {
          case Some(argName) =>
            val maybeFormalIndex = for {
              formalParamNames <- maybeFormalParamNames
              formalIndex      = formalParamNames.indexWhere(ScalaNamesUtil.equivalent(_, argName))
              if formalIndex >= 0
            } yield formalIndex

            maybeFormalIndex.orElse(Option(currentIndex))
          case None => Option(currentIndex)
        }
      }
    }
  }

  private def formalTypeParameterNames(parameterInfo: Any): Option[Seq[String]] = parameterInfo match {
    case ScTypeParameterClauseInfo(params, _) => Option(params.map(_.name))
    case (owner: ScTypeParametersOwner, _) => Option(owner.typeParameters.map(_.name))
    case (method: PsiMethod, _)            => Option(method.getTypeParameters.toSeq.map(_.getName))
    case (clazz: PsiClass, _) =>
      clazz match {
        case td: ScTypeDefinition => Option(td.typeParameters.map(_.name))
        case _                    => Option(clazz.getTypeParameters.toSeq.map(_.getName))
      }
    case _ => None
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
          (tp: ScType) =>
            val needsSpace = param.name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')
            appendPresentableText(if (needsSpace) " : " else ": ", tp)
        }

        if (isBold) makeBold()

        paramText.mkString
      }).mkString(", "))
    }
  }

  private def fromGenericCall(genCall: ScGenericCall): Option[AnyRef] = {
    val precedingValueArgClauses = collectPrecedingValueArgClauses(genCall.referencedExpr)

    val clauseInfo = for {
      ref          <- referenceTargetDeep(genCall)
      resolved     <- fromResolvedElement(ref)
      params       <- MethodResolveProcessor.typeParametersForArgClause(
                        resolved.element,
                        precedingValueArgClauses.size,
                        resolved.isExtensionCall,
                        resolved.exportedInExtension,
                        precedingValueArgClauses
                      )
      scTypeParams = params.flatMap(_.psiTypeParameter.asOptionOf[ScTypeParam])
      if scTypeParams.nonEmpty
    } yield ScTypeParameterClauseInfo(scTypeParams, resolved.substitutor)

    clauseInfo.orElse {
      genCall.referencedExpr match {
        case ref: ScReference => fromResolved(ref)
        case _                => None
      }
    }
  }

  private def collectPrecedingValueArgClauses(expr: ScExpression): Seq[Seq[ScExpression]] = {
    def collect(expr: ScExpression, acc: List[Seq[ScExpression]]): List[Seq[ScExpression]] = expr match {
      case gen: ScGenericCall           => collect(gen.referencedExpr, acc)
      case invocation: MethodInvocation => collect(invocation.getEffectiveInvokedExpr, invocation.argumentExpressions +: acc)
      case paren: ScParenthesisedExpr =>
        paren.innerElement match {
          case Some(inner) => collect(inner, acc)
          case None        => acc
        }
      case _ => acc
    }

    collect(expr, Nil)
  }

  private def fromResolved(ref: ScReference, useActualElement: Boolean = false): Option[(PsiElement, ScSubstitutor)] =
    fromResolvedElement(ref, useActualElement).map(_.toParameterInfo)

  private def fromResolvedElement(ref: ScReference, useActualElement: Boolean = false): Option[ResolvedElement] = {
    ref.bind() match {
      case Some(srr @ ScalaResolveResult.ApplyMethodInnerResolve(inner)) =>
        val resolved =
          if (inner.elementHasTypeParameters) inner
          else                                srr

        Option(ResolvedElement(resolved.element, resolved.substitutor, resolved.isExtensionCall, resolved.exportedInExtension))
      case Some(r @ ScalaResolveResult(m: PsiMethod, substitutor)) =>
        val element = if (useActualElement) r.getActualElement else m
        Option(ResolvedElement(element, substitutor, r.isExtensionCall, r.exportedInExtension))
      case Some(r @ ScalaResolveResult(element @ (_: PsiClass | _: ScTypeParametersOwner), substitutor)) =>
        Option(ResolvedElement(element, substitutor, r.isExtensionCall, r.exportedInExtension))
      case Some(srr) =>
        srr.innerResolveResult.map(x => ResolvedElement(x.getActualElement, x.substitutor, x.isExtensionCall, x.exportedInExtension))
      case _ => None
    }
  }

  override protected def findCall(context: ParameterInfoContext): ScTypeArgs = {
    val (file, offset) = (context.getFile, context.getOffset)
    Option(file.findElementAt(offset)).flatMap { element =>
      Option(PsiTreeUtil.getParentOfType(element, getArgumentListClass)).map { args =>
        context match {
          case context: CreateParameterInfoContext =>
            val res = args.getParent match {
              case genCall: ScGenericCall => fromGenericCall(genCall)
              case ScInfixExpr(_, ref, _) => fromResolved(ref)
              case ScParameterizedTypeElement(typeElement, _) =>
                val maybeReferenceElement = typeElement match {
                  case projection: ScTypeProjection => Option(projection)
                  case ScSimpleTypeElement(reference) => Option(reference)
                  case _ => None
                }

                maybeReferenceElement.flatMap(fromResolved(_, useActualElement = true))
              case _ => None // todo: ScMacroDefinition
            }
            context.setItemsToShow(res.toArray)
          case context: UpdateParameterInfoContext =>
            val typeArgs = args.typeArguments
            val maybeCurrentTypeArg = typeArgs.find(typeArg =>
              typeArg.typeElement.exists(_.getTextRange.containsOffset(offset)) ||
                typeArg.nameElement.exists(_.getTextRange.containsOffset(offset))
            )

            val index = maybeCurrentTypeArg.map(typeArgs.indexOf).filter(_ >= 0).getOrElse(-1)

            context.setCurrentParameter(index)
            context.setHighlightedParameter(
              maybeCurrentTypeArg.getOrElse(element)
            )
          case _ =>
        }

        args
      }
    }.orNull
  }
}

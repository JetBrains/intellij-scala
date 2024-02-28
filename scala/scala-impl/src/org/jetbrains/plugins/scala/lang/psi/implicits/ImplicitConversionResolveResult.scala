package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ExtensionMethod
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

case class ImplicitConversionResolveResult(resolveResult: ScalaResolveResult,
                                           `type`: ScType,
                                           implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                                           unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty)

object ImplicitConversionResolveResult {

  implicit class Ext(private val result: ImplicitConversionResolveResult) extends AnyVal {
    def element: PsiNamedElement = result.resolveResult.element

    def builder: ResolverStateBuilder = new ResolverStateBuilder(result)
  }

  final class ResolverStateBuilder private[ImplicitConversionResolveResult](result: ImplicitConversionResolveResult) {

    ProgressManager.checkCanceled()

    private[this] var innerState: ResolveState = ScalaResolveState.withImplicitConversion(result.resolveResult)

    def state: ResolveState = innerState

    def withType: ResolverStateBuilder = {
      innerState = innerState.withFromType(result.`type`)
      innerState = innerState.withUnresolvedTypeParams(result.unresolvedTypeParameters)
      this
    }

    def withImports: ResolverStateBuilder = {
      innerState = innerState.withImportsUsed(result.resolveResult.importsUsed)
      this
    }

    def withImplicitType: ResolverStateBuilder = {
      innerState = innerState.withImplicitType(result.`type`)
      this
    }
  }

  def processImplicitConversionsAndExtensions(
    refName: Option[String],
    ref: PsiElement,
    processor: BaseProcessor,
    noImplicitsForArgs: Boolean = false,
    forCompletion: Boolean = false,
  )(build: ResolverStateBuilder => ResolverStateBuilder
  )(implicit
    place: ScExpression
  ): Unit =
    processImplicitConversionsAndExtensions(
      refName,
      ref,
      processor,
      this.expressionType,
      noImplicitsForArgs,
      forCompletion
    )(build)

  def processImplicitConversionsAndExtensions(
    refName:            Option[String],
    ref:                PsiElement,
    processor:          BaseProcessor,
    precalculatedType:  Option[ScType],
    noImplicitsForArgs: Boolean,
    forCompletion:      Boolean,
  )(build:              ResolverStateBuilder => ResolverStateBuilder
  )(implicit
    place: PsiElement
  ): Unit =
    for {
      expressionType <- precalculatedType
      if !expressionType.equiv(Nothing) // do not proceed with nothing type, due to performance problems.
      resolveResult <- findImplicitConversionOrExtension(expressionType, refName, ref, processor, noImplicitsForArgs, forCompletion)
    } resolveResult match {
        case srr @ ScalaResolveResult(ext @ ExtensionMethod(), subst) =>
          val state = ScalaResolveState
            .withSubstitutor(subst)
            .withExtensionMethodMarker
            .withRename(srr.renamed)
            .withImportsUsed(srr.importsUsed)
            .withUnresolvedTypeParams(srr.unresolvedTypeParameters.getOrElse(Seq.empty))
          processor.execute(ext, state)
        case conversion =>
          for {
            resultType <- ExtensionConversionHelper.specialExtractParameterType(conversion)
            unresolvedTypeParameters = conversion.unresolvedTypeParameters.getOrElse(Seq.empty)
            result      = ImplicitConversionResolveResult(
              conversion,
              resultType,
              unresolvedTypeParameters = unresolvedTypeParameters,
            )
            state       = build(result.builder).state
            substituted = result.implicitDependentSubstitutor(result.`type`)
          } processor.processType(substituted, place, state)
      }

  def applicable(candidate: ScalaResolveResult, `type`: ScType, place: PsiElement): Option[ImplicitConversionResolveResult] = {
    val substitutor = candidate.substitutor
    for {
      conversion     <- ImplicitConversionData(candidate.element, substitutor)
      application    <- conversion.isApplicable(`type`, place)
      if !application.implicitParameters.exists(_.isNotFoundImplicitParameter)
    } yield ImplicitConversionResolveResult(candidate, application.resultType, substitutor, candidate.unresolvedTypeParameters.getOrElse(Seq.empty))
  }


  private[this] def findImplicitConversionOrExtension(
    expressionType:     ScType,
    refName:            Option[String],
    ref:                PsiElement,
    processor:          BaseProcessor,
    noImplicitsForArgs: Boolean,
    forCompletion:      Boolean
  )(implicit
    place: PsiElement
  ): Seq[ScalaResolveResult] = {
    implicit val elementScope: ElementScope = ElementScope(place)

    val functionType          = FunctionType(Any(place.getProject), Seq(expressionType))
    val expandedFunctionType  = FunctionType(expressionType, arguments(processor, noImplicitsForArgs))

    def checkImplicits(noApplicability: Boolean = false, withoutImplicitsForArgs: Boolean = noImplicitsForArgs): Seq[ScalaResolveResult] = {
      val data = refName.map {
        ExtensionConversionData(place, ref, _, processor, noApplicability, withoutImplicitsForArgs)
      }

      new ImplicitCollector(
        place,
        functionType,
        expandedFunctionType,
        coreElement          = None,
        isImplicitConversion = true,
        extensionData        = data,
        withExtensions       = place.isInScala3Module,
        forCompletion        = forCompletion
      ).collect()
    }

    /**
     * Currently (29 May 2023) scala 3 silently prefers extensions to implicit conversions in case of
     * ambiguity. According to the spec this is wrong, issue was raised by me
     * [[https://github.com/lampepfl/dotty/issues/12904]] in 2021, but nobody seems to care.
     * "Since old style implicit conversions will go away this is a temporary problem." - Martin,
     * yet here in 2023 there are files in the standard library (e.g. IArray) that mix
     * implicit defs and extensions. So this method is here to preserve compatibility with scalac ¯\_(ツ)_/¯ .
     */
    def preferExtensionsToOldStyleImplicitConversions(srrs: Seq[ScalaResolveResult]): Seq[ScalaResolveResult] = {
      val (extensions, rest) = srrs.partition(_.isExtension)

      if (rest.size == 1 && rest.head.element.is[ScFunction]) extensions
      else                                                    Seq.empty
    }

    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    val found = checkImplicits() match {
      case Seq()        => checkImplicits(noApplicability = true)
      case seq @ Seq(_) => seq
      case _            => checkImplicits(withoutImplicitsForArgs = true)
    }

    found match {
      case _ if forCompletion => found
      case Seq(_)             => found
      case multiple           => preferExtensionsToOldStyleImplicitConversions(multiple)
        if (multiple.forall(_.isExtension)) multiple
        else                                preferExtensionsToOldStyleImplicitConversions(multiple)
    }
  }

  private[this] def expressionType(implicit place: ScExpression) =
    place.getTypeWithoutImplicits()
      .map(_.tryExtractDesignatorSingleton)
      .toOption

  private[this] def arguments(processor: BaseProcessor,
                              noImplicitsForArgs: Boolean) = processor match {
    case methodProcessor: MethodResolveProcessor if noImplicitsForArgs =>
      for {
        expressions <- methodProcessor.argumentClauses
        expression <- expressions
        typeResult = expression.getTypeAfterImplicitConversion(checkImplicits = false, isShape = methodProcessor.isShapeResolve, None).tr
      } yield typeResult.getOrAny
    case _ => Seq.empty
  }

}


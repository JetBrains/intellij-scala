package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType}
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
  ): Unit = {
    implicit val context: Context = Context(place)

    for {
      expressionType <- precalculatedType
      if !expressionType.equiv(Nothing) // do not proceed with nothing type, due to performance problems.
      resolveResult <- findImplicitConversionOrExtension(expressionType, refName, ref, processor, noImplicitsForArgs, forCompletion)
    } resolveResult match {
        case srr if srr.isExtensionCall =>
          val state = ScalaResolveState
            .withSubstitutor(srr.substitutor)
            .withExtensionMethodMarker
            .withRename(srr.renamed)
            .withImportsUsed(srr.importsUsed)
            .withUnresolvedTypeParams(srr.unresolvedTypeParameters.getOrElse(Seq.empty))
            .withExportedInfo(srr.exportedInfo)
            .withImplicitScopeObject(srr.implicitScopeObject)
            .withIsExtensionFromGiven(srr.isExtensionFromGiven)

          processor.execute(srr.element, state)
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
  }

  def applicable(candidate: ScalaResolveResult, `type`: ScType, place: PsiElement): Option[ImplicitConversionResolveResult] = {
    val substitutor = candidate.substitutor
    for {
      conversion     <- ImplicitConversionData(candidate.element, substitutor)
      application    <- conversion.isApplicable(`type`, place)
      if !application.implicitArgsByClause.exists(_.args.exists(_.isNotFoundImplicitParameter))
    } yield
      ImplicitConversionResolveResult(
        candidate,
        application.resultType,
        substitutor,
        candidate.unresolvedTypeParameters.getOrElse(Seq.empty)
      )
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

    def checkImplicits(
      noApplicability:         Boolean = false,
      withoutImplicitsForArgs: Boolean = noImplicitsForArgs
    ): Seq[ScalaResolveResult] = {
      val data = refName.map(
        ExtensionConversionData(
          place,
          ref,
          _,
          processor,
          noApplicability,
          withoutImplicitsForArgs
        )
      )

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

    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    val found = checkImplicits() match {
      case Seq()        => checkImplicits(noApplicability = true)
      case seq @ Seq(_) => seq
      case _            => checkImplicits(withoutImplicitsForArgs = true)
    }

    found
  }

  private[this] def expressionType(implicit place: ScExpression) =
    place.getTypeWithoutImplicits()
      .map(_.tryExtractDesignatorSingleton)
      .toOption

  private[this] def arguments(processor: BaseProcessor, noImplicitsForArgs: Boolean) =
    processor match {
      case methodProcessor: MethodResolveProcessor if noImplicitsForArgs =>
        for {
          expressions <- methodProcessor.argumentClauses
          expression  <- expressions
          typeResult   =
            expression.getTypeAfterImplicitConversion(
              checkImplicits = false,
              isShape        = methodProcessor.isShapeResolve,
              None
            ).tr
        } yield typeResult.getOrAny
      case _ => Seq.empty
    }

}


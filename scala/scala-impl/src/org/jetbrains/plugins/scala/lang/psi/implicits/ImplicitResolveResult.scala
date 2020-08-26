package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}


/**
  * @author adkozlov
  */
sealed trait ImplicitResolveResult {

  val `type`: ScType

  val implicitDependentSubstitutor: ScSubstitutor

  protected val resolveResult: ScalaResolveResult

  protected val unresolvedTypeParameters: collection.Seq[TypeParameter]
}

final case class CompanionImplicitResolveResult(override val resolveResult: ScalaResolveResult,
                                                override val `type`: ScType,
                                                override val implicitDependentSubstitutor: ScSubstitutor) extends ImplicitResolveResult {
  override val unresolvedTypeParameters: collection.Seq[TypeParameter] = Seq.empty
}

final case class RegularImplicitResolveResult(override val resolveResult: ScalaResolveResult,
                                              override val `type`: ScType,
                                              override val implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                                              override val unresolvedTypeParameters: collection.Seq[TypeParameter] = Seq.empty) extends ImplicitResolveResult

object ImplicitResolveResult {

  implicit class Ext(private val result: ImplicitResolveResult) extends AnyVal {
    def element: PsiNamedElement = result.resolveResult.element

    def builder: ResolverStateBuilder = new ResolverStateBuilder(result)
  }

  final class ResolverStateBuilder private[ImplicitResolveResult](result: ImplicitResolveResult) {

    ProgressManager.checkCanceled()

    private[this] var innerState: ResolveState = ScalaResolveState.withImplicitConversion(result.resolveResult)

    def state: ResolveState = innerState

    def withType: ResolverStateBuilder = {
      innerState = innerState.withFromType(result.`type`)
      innerState = innerState.withUnresolvedTypeParams(result.unresolvedTypeParameters.toSeq)
      this
    }

    def withImports: ResolverStateBuilder = {
      innerState = innerState.withImportsUsed(result.resolveResult.importsUsed.toSet)
      this
    }

    def withImplicitType: ResolverStateBuilder = {
      innerState = innerState.withImplicitType(result.`type`)
      this
    }
  }

  def processImplicitConversions(refName: String,
                                 ref: ScExpression,
                                 processor: BaseProcessor,
                                 noImplicitsForArgs: Boolean = false,
                                 precalculatedType: Option[ScType] = None)
                                (build: ResolverStateBuilder => ResolverStateBuilder)
                                (implicit place: ScExpression): Unit = for {
    expressionType <- precalculatedType.orElse(this.expressionType)
    if !expressionType.equiv(Nothing) // do not proceed with nothing type, due to performance problems.

    resolveResult <- findImplicitConversion(expressionType, refName, ref, processor, noImplicitsForArgs)
    resultType <- ExtensionConversionHelper.specialExtractParameterType(resolveResult)

    unresolvedTypeParameters = resolveResult.unresolvedTypeParameters.getOrElse(Seq.empty)
    result = RegularImplicitResolveResult(resolveResult, resultType, unresolvedTypeParameters = unresolvedTypeParameters) //todo: from companion parameter

    builder = build(result.builder)

    substituted = result.implicitDependentSubstitutor(result.`type`)
  } processor.processType(substituted, place, builder.state)

  private[this] def findImplicitConversion(expressionType: ScType,
                                           refName: String, ref: ScExpression,
                                           processor: BaseProcessor,
                                           noImplicitsForArgs: Boolean)
                                          (implicit place: ScExpression) = {
    import place.elementScope
    val functionType = FunctionType(Any(place.projectContext), Seq(expressionType))
    val expandedFunctionType = FunctionType(expressionType, arguments(processor, noImplicitsForArgs))

    def checkImplicits(noApplicability: Boolean = false, withoutImplicitsForArgs: Boolean = noImplicitsForArgs): collection.Seq[ScalaResolveResult] = {
      val data = ExtensionConversionData(place, ref, refName, processor, noApplicability, withoutImplicitsForArgs)

      new ImplicitCollector(
        place,
        functionType,
        expandedFunctionType,
        coreElement = None,
        isImplicitConversion = true,
        extensionData = Some(data)
      ).collect()
    }

    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    val foundImplicits = checkImplicits() match {
      case collection.Seq() => checkImplicits(noApplicability = true)
      case seq@collection.Seq(_) => seq
      case _ => checkImplicits(withoutImplicitsForArgs = true)
    }

    foundImplicits match {
      case collection.Seq(resolveResult) => Some(resolveResult)
      case _ => None
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


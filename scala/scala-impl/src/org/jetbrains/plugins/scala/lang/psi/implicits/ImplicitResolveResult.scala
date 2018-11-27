package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor}

/**
  * @author adkozlov
  */
sealed trait ImplicitResolveResult {

  val `type`: ScType

  val implicitDependentSubstitutor: ScSubstitutor

  protected val resolveResult: ScalaResolveResult

  protected val unresolvedTypeParameters: Seq[TypeParameter]
}

final case class CompanionImplicitResolveResult(resolveResult: ScalaResolveResult,
                                                `type`: ScType,
                                                implicitDependentSubstitutor: ScSubstitutor) extends ImplicitResolveResult {
  override val unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty
}

final case class RegularImplicitResolveResult(resolveResult: ScalaResolveResult,
                                              `type`: ScType,
                                              implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                                              unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty) extends ImplicitResolveResult

object ImplicitResolveResult {

  implicit class Ext(private val result: ImplicitResolveResult) extends AnyVal {
    def element: PsiNamedElement = result.resolveResult.element

    def builder: ResolverStateBuilder = new ResolverStateBuilder(result)
  }

  final class ResolverStateBuilder private[ImplicitResolveResult](result: ImplicitResolveResult) {

    ProgressManager.checkCanceled()

    private[this] var innerState: ResolveState = {
      val state = ResolveState.initial.put(IMPLICIT_FUNCTION, result.resolveResult)

      result.element.getParent match {
        case body: ScTemplateBody =>
          val parent = PsiTreeUtil.getParentOfType(body, classOf[PsiClass])
          state.put(IMPLICIT_RESOLUTION, parent)
        case _ => state
      }
    }

    def state: ResolveState = innerState

    def withType: ResolverStateBuilder = {
      innerState = innerState.put(BaseProcessor.FROM_TYPE_KEY, result.`type`)
      innerState.put(BaseProcessor.UNRESOLVED_TYPE_PARAMETERS_KEY, result.unresolvedTypeParameters)
      this
    }

    def withImports: ResolverStateBuilder = {
      innerState = innerState.put(ImportUsed.key, result.resolveResult.importsUsed)
      this
    }

    def withImplicitType: ResolverStateBuilder = {
      innerState = innerState.put(IMPLICIT_TYPE, result.`type`)
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

    result = RegularImplicitResolveResult(resolveResult, resultType, unresolvedTypeParameters = resolveResult.unresolvedTypeParameters.getOrElse(Seq.empty)) //todo: from companion parameter

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

    def checkImplicits(noApplicability: Boolean = false, withoutImplicitsForArgs: Boolean = noImplicitsForArgs): Seq[ScalaResolveResult] = {
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
      case Seq() => checkImplicits(noApplicability = true)
      case seq@Seq(_) => seq
      case _ => checkImplicits(withoutImplicitsForArgs = true)
    }

    foundImplicits match {
      case Seq(resolveResult) => Some(resolveResult)
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
        (typeResult, _) = expression.getTypeAfterImplicitConversion(checkImplicits = false, isShape = methodProcessor.isShapeResolve, None)
      } yield typeResult.getOrAny
    case _ => Seq.empty
  }

}


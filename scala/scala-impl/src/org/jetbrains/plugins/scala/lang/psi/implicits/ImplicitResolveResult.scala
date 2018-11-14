package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, Nothing, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Seq

/**
  * @author adkozlov
  */
sealed trait ImplicitResolveResult {
  def element: PsiNamedElement = resolveResult.element

  val `type`: ScType

  def typeWithDependentSubstitutor: ScType = implicitDependentSubstitutor.subst(`type`)

  def substitutor: ScSubstitutor =
    implicitDependentSubstitutor.followed(resolveResult.substitutor)

  protected val resolveResult: ScalaResolveResult

  protected val unresolvedTypeParameters: Seq[TypeParameter]

  protected val implicitDependentSubstitutor: ScSubstitutor
}

case class CompanionImplicitResolveResult(resolveResult: ScalaResolveResult,
                                          `type`: ScType,
                                          implicitDependentSubstitutor: ScSubstitutor) extends ImplicitResolveResult {
  override val unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty
}

case class RegularImplicitResolveResult(resolveResult: ScalaResolveResult,
                                        `type`: ScType,
                                        implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                                        unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty) extends ImplicitResolveResult

object ImplicitResolveResult {

  class ResolverStateBuilder(result: ImplicitResolveResult) {

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

  def findImplicitConversion(baseExpr: ScExpression,
                             refName: String,
                             ref: ScExpression,
                             processor: BaseProcessor,
                             noImplicitsForArgs: Boolean = false,
                             precalcType: Option[ScType] = None): Option[ImplicitResolveResult] = {
    implicit val ctx: ProjectContext = baseExpr

    val exprType: ScType = precalcType match {
      case None => ImplicitCollector.exprType(baseExpr, fromUnder = false) match {
        case None => return None
        case Some(x) if x.equiv(Nothing) => return None //do not proceed with nothing type, due to performance problems.
        case Some(x) => x
      }
      case Some(x) if x.equiv(Nothing) => return None
      case Some(x) => x
    }
    val args = processor match {
      case _ if !noImplicitsForArgs => Seq.empty
      case m: MethodResolveProcessor => m.argumentClauses.flatMap { expressions =>
        expressions.map {
          _.getTypeAfterImplicitConversion(checkImplicits = false, isShape = m.isShapeResolve, None)._1.getOrAny
        }
      }
      case _ => Seq.empty
    }

    def checkImplicits(noApplicability: Boolean = false, withoutImplicitsForArgs: Boolean = noImplicitsForArgs): Seq[ScalaResolveResult] = {
      val data = ExtensionConversionData(baseExpr, ref, refName, processor, noApplicability, withoutImplicitsForArgs)

      implicit val elementScope: ElementScope = baseExpr.elementScope
      new ImplicitCollector(
        baseExpr,
        FunctionType(Any, Seq(exprType)),
        FunctionType(exprType, args),
        coreElement = None,
        isImplicitConversion = true,
        extensionData = Some(data)).collect()
    }

    //This logic is important to have to navigate to problematic method, in case of failed resolve.
    //That's why we need to have noApplicability parameter
    val foundImplicits = checkImplicits() match {
      case Seq() => checkImplicits(noApplicability = true)
      case seq@Seq(_) => seq
      case _ => checkImplicits(withoutImplicitsForArgs = true)
    }

    foundImplicits match {
      case Seq(resolveResult) =>
        ExtensionConversionHelper.specialExtractParameterType(resolveResult).map {
          case (tp, typeParams) =>
            RegularImplicitResolveResult(resolveResult, tp, unresolvedTypeParameters = typeParams) //todo: from companion parameter
        }
      case _ => None
    }
  }

}


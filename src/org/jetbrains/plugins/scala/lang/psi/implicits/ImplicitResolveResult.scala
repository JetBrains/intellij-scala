package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

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
    private[this] var innerState: ResolveState = ResolveState.initial

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

    def withImplicitFunction: ResolverStateBuilder = {
      innerState = innerState.put(IMPLICIT_FUNCTION, result.resolveResult)
      elementParent.foreach { parent =>
        innerState = innerState.put(IMPLICIT_RESOLUTION, parent)
      }
      this
    }

    private def elementParent =
      Option(result.element).map {
        _.getParent
      }.collect {
        case body: ScTemplateBody => body
      }.map {
        PsiTreeUtil.getParentOfType(_, classOf[PsiClass])
      }
  }

}


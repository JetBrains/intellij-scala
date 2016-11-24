package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiNamedElement, ResolveState}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitMapResult
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

/**
  * @author adkozlov
  */
sealed trait ImplicitResolveResult {
  val `type`: ScType

  val resolveResult: ScalaResolveResult

  val unresolvedTypeParameters: Seq[TypeParameter]

  def element: PsiNamedElement =
    resolveResult.element

  def substitutor: ScSubstitutor =
    implicitDependentSubstitutor.followed(resolveResult.substitutor)

  def getTypeWithDependentSubstitutor: ScType = implicitDependentSubstitutor.subst(`type`)

  protected val implicitDependentSubstitutor: ScSubstitutor
}

case class CompanionImplicitResolveResult(`type`: ScType,
                                          result: ImplicitMapResult) extends ImplicitResolveResult {
  override val resolveResult: ScalaResolveResult = result.resolveResult
  override protected val implicitDependentSubstitutor: ScSubstitutor = result.implicitDependentSubst
  override val unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty
}

case class RegularImplicitResolveResult(`type`: ScType,
                                        resolveResult: ScalaResolveResult,
                                        implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty,
                                        unresolvedTypeParameters: Seq[TypeParameter] = Seq.empty) extends ImplicitResolveResult

object RegularImplicitResolveResult {
  def apply(`type`: ScType, result: ImplicitMapResult): RegularImplicitResolveResult =
    RegularImplicitResolveResult(`type`, result.resolveResult, result.implicitDependentSubst)
}

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
      innerState = innerState.put(CachesUtil.IMPLICIT_TYPE, result.`type`)
      this
    }

    def withImplicitFunction: ResolverStateBuilder = {
      innerState = innerState.put(CachesUtil.IMPLICIT_FUNCTION, result.element)
      elementParent.foreach { parent =>
        innerState = innerState.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, parent)
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


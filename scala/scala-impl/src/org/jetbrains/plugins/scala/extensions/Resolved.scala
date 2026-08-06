package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

object ResolvedWithSubst {

  def unapply(reference: PsiReference): Option[(PsiElement, ScSubstitutor)] = reference match {
    case Resolved(ScalaResolveResult(target, substitutor)) => Some(target, substitutor)
    case ResolvesTo(target) => Some(target, ScSubstitutor.empty)
    case _ => None
  }
}

object ResolvesTo {
  def unapply(reference: PsiReference): Option[PsiElement] = Option(reference.resolve())
}

object Resolved {
  def unapply(reference: ScReference): Option[ScalaResolveResult] = reference.bind()
}
package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Pavel Fatin
 */

object ResolvedWithSubst {

  def unapply(reference: PsiReference): Option[(PsiElement, ScSubstitutor)] = reference match {
    case Resolved(ScalaResolveResult(target, substitutor)) => Some(target, substitutor)
    case ResolvesTo(target) => Some(target, ScSubstitutor.empty)
    case _ => None
  }
}

object ResolvesTo {

  def unapply(reference: PsiReference): Option[PsiElement] = reference match {
    case null => None
    case _ => Option(reference.resolve())
  }
}

object Resolved {

  def unapply(reference: ScReferenceElement): Option[ScalaResolveResult] = reference match {
    case null => None
    case _ => reference.bind()
  }
}
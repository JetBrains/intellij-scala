package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Pavel Fatin
 */

object ResolvedWithSubst {
  def unapply(e: PsiReference): Option[(PsiElement, ScSubstitutor)] = {
    e match {
      case null => None
      case e: ScReferenceElement => e.bind() match {
        case Some(ScalaResolveResult(target, substitutor)) => Some(target, substitutor)
        case _ => None
      }
      case _ => Option(e.resolve).map((_, ScSubstitutor.empty))
    }
  }
}

object ResolvesTo {
  def unapply(ref: PsiReference): Option[PsiElement] = {
    ref match {
      case null => None
      case r => Option(r.resolve())
    }
  }
}

object Resolved {
  def unapply(e: ScReferenceElement): Option[ScalaResolveResult] = e.bind()
}
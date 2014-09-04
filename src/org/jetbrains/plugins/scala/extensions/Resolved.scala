package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Pavel Fatin
 */

object Resolved {
  def unapply(e: PsiReference): Option[(PsiElement, ScSubstitutor)] = {
    if (e == null) {
      None
    } else {
      e match {
        case e: ScReferenceElement => e.bind match {
          case Some(ScalaResolveResult(target, substitutor)) => Some(target, substitutor)
          case _ => None
        }
        case _ =>
          val target = e.resolve
          if (target == null) None
          else Some(target, ScSubstitutor.empty)
      }
    }
  }
}
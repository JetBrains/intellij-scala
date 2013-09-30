package org.jetbrains.plugins.scala
package findUsages
package apply

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import lang.psi.api.base.patterns.ScConstructorPattern
import lang.psi.api.base.ScStableCodeReferenceElement

class UnapplyMethodSearcher extends ApplyUnapplyMethodSearcherBase {

  protected val names: Set[String] = Set("unapply", "unapplySeq")

  protected def checkAndTransform(ref: PsiReference): Option[ResolvableReferenceElement] =
    (ref, ref.getElement.getContext) match {
      case (sref: ScStableCodeReferenceElement, x: ScConstructorPattern) => Some(sref)
      // TODO check every other ScConstructorPattern known to man?
      case _ => None
    }
}
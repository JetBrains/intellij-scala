package org.jetbrains.plugins.scala
package findUsages
package apply

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}

class UnapplyMethodSearcher extends ApplyUnapplyMethodSearcherBase {

  protected val names: Set[String] = Set("unapply", "unapplySeq")

  protected def checkAndTransform(ref: PsiReference): Option[ScReferenceElement] =
    (ref, ref.getElement.getContext) match {
      case (sref: ScStableCodeReferenceElement, _: ScConstructorPattern) => Some(sref)
      // TODO check every other ScConstructorPattern known to man?
      case _ => None
    }
}

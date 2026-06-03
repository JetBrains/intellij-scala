package org.jetbrains.plugins.scala.findUsages.apply

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScExtractorPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}

class UnapplyMethodSearcher extends ApplyUnapplyMethodSearcherBase {

  override protected val names: Set[String] = Set("unapply", "unapplySeq")

  override protected def checkAndTransform(ref: PsiReference): Option[ScReference] =
    (ref, ref.getElement.getContext) match {
      case (sref: ScStableCodeReference, _: ScExtractorPattern) =>
        Some(sref)
      case _ => None
    }
}

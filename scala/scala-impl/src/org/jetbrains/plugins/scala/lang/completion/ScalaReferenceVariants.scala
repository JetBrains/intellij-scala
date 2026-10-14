package org.jetbrains.plugins.scala.lang.completion

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.resolve.{ReferenceVariants, ScalaResolveResult}

final class ScalaReferenceVariants extends ReferenceVariants {
  override def lookupElements(reference: ScReference, variants: Array[ScalaResolveResult]): Array[Object] =
    variants.map[Object](_.createLookupElement(isInImport = isInImport(reference)))
}

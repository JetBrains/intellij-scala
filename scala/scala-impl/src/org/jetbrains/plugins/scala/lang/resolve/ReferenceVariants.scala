package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

/**
 * Turns the completion variants of a reference into lookup elements, see [[com.intellij.psi.PsiReference.getVariants]].
 * Lookup elements are completion code, which implements this interface, so the PSI knows only the interface.
 */
@ApiStatus.Internal
trait ReferenceVariants {
  def lookupElements(reference: ScReference, variants: Array[ScalaResolveResult]): Array[Object]
}

object ReferenceVariants {
  def instance: ReferenceVariants = ApplicationManager.getApplication.getService(classOf[ReferenceVariants])
}

package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.{PsiElement, PsiPolyVariantReference, ResolveResult}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ScPolyResolvable extends PsiPolyVariantReference with PsiElement {
  def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult]

  @deprecated("Is required for compatibility. Prefer `multiResolveScala` for better type inference.", "2018.1")
  override final def multiResolve(incomplete: Boolean): Array[ResolveResult] = multiResolveScala(incomplete).toArray
}

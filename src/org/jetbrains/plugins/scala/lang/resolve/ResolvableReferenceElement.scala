package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiPolyVariantReference, _}
import org.jetbrains.annotations.TestOnly


trait ResolvableReferenceElement extends PsiPolyVariantReference {
  def resolve(): PsiElement = {
    bind() match {
      case Some(result) if !result.isCyclicReference => result.element
      case _ => null
    }
  }

  @TestOnly
  def advancedResolve: Option[ScalaResolveResult] = {
    bind() match {
      case Some(result) if !result.isCyclicReference =>  Some(result)
      case _ => None
    }
  }

  def bind(): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()
    val results = multiResolve(false)
    if (results.length == 1) Some(results(0).asInstanceOf[ScalaResolveResult]) else None
  }
}
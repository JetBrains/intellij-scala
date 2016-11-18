package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiPolyVariantReference, _}
import org.jetbrains.annotations.TestOnly


trait ResolvableReferenceElement extends PsiPolyVariantReference {
  def resolve(): PsiElement = {
    this.bind() match {
      case Some(result) if !result.isCyclicReference => result.element
      case _ => null
    }
  }

  @TestOnly
  def advancedResolve: Option[ScalaResolveResult] = {
    this.bind() match {
      case Some(result) if !result.isCyclicReference =>  Some(result)
      case _ => None
    }
  }
}

object ResolvableReferenceElement {
  implicit class ResolvableReferenceElementExt(val elem: ResolvableReferenceElement) extends AnyVal {
    @inline
    def bind(): Option[ScalaResolveResult] = {
      ProgressManager.checkCanceled()
      val results = elem.multiResolve(false).collect{case srr: ScalaResolveResult => srr}
      if (results.length == 1) Some(results(0)) else None
    }
  }
}
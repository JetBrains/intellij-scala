package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import _root_.org.jetbrains.plugins.scala.lang.resolve._
import com.intellij.psi._
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.openapi.progress.ProgressManager


trait ResolvableReferenceElement extends PsiPolyVariantReference {
  def resolve(): PsiElement = {
    advancedResolve match {
      case Some(result) => result.element
      case _ => null
    }
  }

  def advancedResolve: Option[ScalaResolveResult] = {
    bind match {
      case Some(result) if !result.isCyclicReference =>  Some(result)
      case _ => None
    }
  }

  def bind(): Option[ScalaResolveResult] = {
     ProgressManager.checkCanceled
     val results = multiResolve(false)
     if(results.length == 1) Some(results(0).asInstanceOf[ScalaResolveResult]) else None
   }
}
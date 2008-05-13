package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiPolyVariantReference {
  def bind() : ScalaResolveResult = {
    val results = multiResolve(false)
    results.length match {
      case 1 => results(0).asInstanceOf[ScalaResolveResult]
      case _ => ScalaResolveResult.empty
    }
  }

  def resolve(): PsiElement = bind.element

  def getType() : ScType
}
package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiReference {
  def bind() : ScalaResolveResult = new ScalaResolveResult(null, ScSubstitutor.empty) //todo
}
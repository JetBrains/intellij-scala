package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.resolve.ScalaClassRefResolveResult

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScReferenceElement extends ScalaPsiElement with PsiReference {
  def bind() : ScalaClassRefResolveResult
}
package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import psi.ScalaPsiElement
import toplevel.ScNamedElement
import types.ScType
import com.intellij.psi._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeParam extends ScalaPsiElement with ScNamedElement with PsiTypeParameter {
  def lowerBound() : ScType
  def upperBound() : ScType
}
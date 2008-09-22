package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner, ScPolymorphicElement}
import types.{ScTypeVariable, ScType}
import psi.ScalaPsiElement
import com.intellij.psi._
import toplevel.typedef.ScTypeDefinition

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScTypeParam extends ScalaPsiElement with ScPolymorphicElement with PsiTypeParameter {
  def isCovariant() : Boolean
  def isContravariant() : Boolean

  def owner : ScTypeParametersOwner
}
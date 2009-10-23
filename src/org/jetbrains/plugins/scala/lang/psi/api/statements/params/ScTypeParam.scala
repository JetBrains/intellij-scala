package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import toplevel.{ScTypeParametersOwner, ScPolymorphicElement}
import psi.ScalaPsiElement
import com.intellij.psi._

/**
 * @author Alexander Podkhalyuzin
 */

trait ScTypeParam extends ScalaPsiElement with ScPolymorphicElement with PsiTypeParameter {
  def isCovariant(): Boolean

  def isContravariant(): Boolean

  def owner: ScTypeParametersOwner
}
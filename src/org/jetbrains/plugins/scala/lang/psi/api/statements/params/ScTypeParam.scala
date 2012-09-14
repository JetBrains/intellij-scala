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
  import ScTypeParam._

  def isCovariant: Boolean

  def isContravariant: Boolean

  def variance: Int = if (isCovariant) Covariant else if (isContravariant) Contravariant else Invariant

  def owner: ScTypeParametersOwner

  def getOffsetInFile: Int

  def getContainingFileName: String

  def typeParameterText: String
}

object ScTypeParam {
  val Covariant = 1
  val Invariant = 0
  val Contravariant = -1
}
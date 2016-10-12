package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPolymorphicElement, ScTypeParametersOwner}

/**
 * @author Alexander Podkhalyuzin
 */

trait ScTypeParam extends ScalaPsiElement with ScPolymorphicElement with PsiTypeParameter with ScAnnotationsHolder {
  import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam._

  def isCovariant: Boolean

  def isContravariant: Boolean

  def variance: Int = if (isCovariant) Covariant else if (isContravariant) Contravariant else Invariant

  def owner: ScTypeParametersOwner

  def getOffsetInFile: Int

  def getContainingFileName: String

  def typeParameterText: String

  def isHigherKindedTypeParameter: Boolean
}

object ScTypeParam {
  val Covariant = 1
  val Invariant = 0
  val Contravariant = -1
}

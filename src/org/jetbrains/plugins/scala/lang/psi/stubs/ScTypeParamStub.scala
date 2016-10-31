package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBoundsOwnerStub

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
trait ScTypeParamStub extends ScBoundsOwnerStub[ScTypeParam] {
  def viewBoundsTexts: Array[String]

  def viewBoundsTypeElements: Seq[ScTypeElement]

  def contextBoundsTexts: Array[String]

  def contextBoundsTypeElements: Seq[ScTypeElement]

  def isCovariant: Boolean

  def isContravariant: Boolean

  def positionInFile: Int

  def containingFileName: String

  def text: String
}
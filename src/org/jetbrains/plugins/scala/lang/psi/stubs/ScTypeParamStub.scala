package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
trait ScTypeParamStub extends NamedStub[ScTypeParam] {
  def upperBoundText: Option[String]

  def upperBoundTypeElement: Option[ScTypeElement]

  def lowerBoundText: Option[String]

  def lowerBoundTypeElement: Option[ScTypeElement]

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
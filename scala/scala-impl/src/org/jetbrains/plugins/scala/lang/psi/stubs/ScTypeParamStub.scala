package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBoundsOwnerStub

trait ScTypeParamStub extends ScBoundsOwnerStub[ScTypeParam] {
  def viewBoundsTexts: Array[String]

  def viewBoundsTypeElements: Seq[ScTypeElement]

  def contextBoundsTexts: Array[String]

  def contextBounds: Seq[ScContextBound]

  def isCovariant: Boolean

  def isContravariant: Boolean

  def containingFileName: String

  def text: String
}
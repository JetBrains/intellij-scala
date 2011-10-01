package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.base.types.ScTypeElement
import api.statements.params.ScTypeParam
import com.intellij.psi.stubs.NamedStub
import types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScTypeParamStub extends NamedStub[ScTypeParam]{
  def getUpperText: String
  def getLowerText: String
  def getViewText: Array[String]
  def getContextBoundText: Array[String]
  def getUpperTypeElement: Option[ScTypeElement]
  def getLowerTypeElement: Option[ScTypeElement]
  def getViewTypeElement: Array[ScTypeElement]
  def getContextBoundTypeElement: Array[ScTypeElement]
  def isCovariant: Boolean
  def isContravariant: Boolean
  def getPositionInFile: Int
  def getContainingFileName: String
}
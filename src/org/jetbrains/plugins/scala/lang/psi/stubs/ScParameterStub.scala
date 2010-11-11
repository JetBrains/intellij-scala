package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import api.statements.params.ScParameter
import com.intellij.psi.stubs.NamedStub
import api.base.types.ScTypeElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

trait ScParameterStub extends NamedStub[ScParameter] {
  def getTypeElement: Option[ScTypeElement]

  def getTypeText: String

  def isStable: Boolean

  def isDefaultParam: Boolean

  def isRepeated: Boolean

  def isVal: Boolean

  def isVar: Boolean
}
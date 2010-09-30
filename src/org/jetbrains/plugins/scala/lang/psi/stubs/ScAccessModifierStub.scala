package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.base.ScAccessModifier
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScAccessModifierStub extends StubElement[ScAccessModifier] {
  def isPrivate: Boolean
  def isProtected: Boolean
  def isThis: Boolean
  def getIdText: Option[String]
}
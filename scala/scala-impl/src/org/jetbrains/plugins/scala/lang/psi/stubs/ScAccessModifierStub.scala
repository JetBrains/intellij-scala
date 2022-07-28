package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier

trait ScAccessModifierStub extends StubElement[ScAccessModifier] {
  def isPrivate: Boolean

  def isProtected: Boolean

  def isThis: Boolean

  def idText: Option[String]
}
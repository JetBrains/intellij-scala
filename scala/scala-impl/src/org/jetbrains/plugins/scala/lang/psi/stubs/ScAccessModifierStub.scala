package org.jetbrains.plugins.scala.lang.psi.stubs


import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier

trait ScAccessModifierStub extends ScStubElement[ScAccessModifier] {
  def isPrivate: Boolean

  def isProtected: Boolean

  def isThis: Boolean

  def idText: Option[String]
}
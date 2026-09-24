package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.util.EnumSet._

trait ScModifiersStub extends ScStubElement[ScModifierList] {
  def modifiers: EnumSet[ScalaModifier]
}
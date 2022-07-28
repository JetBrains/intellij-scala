package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.util.EnumSet._

trait ScModifiersStub extends StubElement[ScModifierList] {
  def modifiers: EnumSet[ScalaModifier]
}
package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

/**
  * User: Alexander Podkhalyuzin
  * Date: 21.01.2009
  */
trait ScModifiersStub extends StubElement[ScModifierList] {
  def hasExplicitModifiers: Boolean

  def modifiers: Array[String]
}
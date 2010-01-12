package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.{NamedStub, StubElement}
import api.base.types.{ScTypeElement, ScSelfTypeElement}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */

trait ScSelfTypeElementStub extends NamedStub[ScSelfTypeElement]{
  def getTypeElementText: String
}
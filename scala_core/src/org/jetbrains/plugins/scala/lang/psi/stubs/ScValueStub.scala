package org.jetbrains.plugins.scala.lang.psi.stubs

import api.statements.ScValue
import com.intellij.psi.stubs.{StubElement, NamedStub}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

trait ScValueStub extends StubElement[ScValue] {
  def isDeclaration: Boolean
  def getNames: Array[String]
}
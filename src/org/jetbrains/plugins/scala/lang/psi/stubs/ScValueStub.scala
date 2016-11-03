package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */
trait ScValueStub extends ScValueOrVariableStub[ScValue] {
  def isImplicit: Boolean
}
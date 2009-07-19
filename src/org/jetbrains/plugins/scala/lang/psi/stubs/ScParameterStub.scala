package org.jetbrains.plugins.scala.lang.psi.stubs

import api.statements.params.ScParameter
import com.intellij.psi.stubs.NamedStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

trait ScParameterStub extends NamedStub[ScParameter] {
  def getTypeText: String

  def isStable: Boolean
}
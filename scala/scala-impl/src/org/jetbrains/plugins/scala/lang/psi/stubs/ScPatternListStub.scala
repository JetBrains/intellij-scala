package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList

trait ScPatternListStub extends ScStubElement[ScPatternList] {
  def simplePatterns: Boolean
}
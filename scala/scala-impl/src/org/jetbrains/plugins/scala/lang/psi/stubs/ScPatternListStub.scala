package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList

trait ScPatternListStub extends StubElement[ScPatternList] {
  def simplePatterns: Boolean
}
package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList

trait ScPatternListStub extends StubElement[ScPatternList] {
  def simplePatterns: Boolean
}
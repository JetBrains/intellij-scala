package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.base.ScPatternList
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2009
 */

trait ScPatternListStub extends StubElement[ScPatternList] {
  def allPatternsSimple: Boolean
}
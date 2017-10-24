package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */
trait ScImportSelectorsStub extends StubElement[ScImportSelectors] {
  def hasWildcard: Boolean
}
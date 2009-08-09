package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.toplevel.imports.ScImportSelectors
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

trait ScImportSelectorsStub extends StubElement[ScImportSelectors] {
  def hasWildcard: Boolean
}
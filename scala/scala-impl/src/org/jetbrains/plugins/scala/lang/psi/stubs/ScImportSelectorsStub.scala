package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors

trait ScImportSelectorsStub extends StubElement[ScImportSelectors] {
  def hasWildcard: Boolean
}
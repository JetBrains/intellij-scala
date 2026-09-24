package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors

trait ScImportSelectorsStub extends ScStubElement[ScImportSelectors] {
  def hasWildcard: Boolean
}
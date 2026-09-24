package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

trait ScImportExprStub extends ScStubElement[ScImportExpr] {
  def referenceText: Option[String]

  def reference: Option[ScStableCodeReference]

  def hasWildcardSelector: Boolean

  def hasGivenSelector: Boolean
}
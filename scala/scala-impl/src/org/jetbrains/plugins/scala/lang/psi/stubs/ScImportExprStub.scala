package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

trait ScImportExprStub extends StubElement[ScImportExpr] {
  def referenceText: Option[String]

  def reference: Option[ScStableCodeReference]

  def hasWildcardSelector: Boolean

  def hasGivenSelector: Boolean
}
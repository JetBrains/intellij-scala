package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.base.ScStableCodeReferenceElement
import api.toplevel.imports.ScImportExpr
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

trait ScImportExprStub extends StubElement[ScImportExpr] {
  def reference: Option[ScStableCodeReferenceElement]

  def isSingleWildcard: Boolean
}
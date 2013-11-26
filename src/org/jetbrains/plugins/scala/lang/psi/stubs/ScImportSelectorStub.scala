package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.base.ScStableCodeReferenceElement
import api.toplevel.imports.ScImportSelector
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

trait ScImportSelectorStub extends StubElement[ScImportSelector] {
  def isAliasedImport: Boolean

  def reference: ScStableCodeReferenceElement

  def importedName: String

  def isAliasImport: Boolean
}
package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

trait ScImportSelectorStub extends StubElement[ScImportSelector] {
  def isAliasedImport: Boolean

  def referenceText: Option[String]

  def reference: Option[ScStableCodeReferenceElement]

  def importedName: Option[String]
}
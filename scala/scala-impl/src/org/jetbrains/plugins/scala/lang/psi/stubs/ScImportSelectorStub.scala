package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

trait ScImportSelectorStub extends StubElement[ScImportSelector] with ScTypeElementOwnerStub[ScImportSelector] {
  def isAliasedImport: Boolean

  def referenceText: Option[String]

  def reference: Option[ScStableCodeReference]

  def isWildcardSelector: Boolean

  def importedName: Option[String]

  def isGivenSelector: Boolean
}
package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

trait ScImportSelectorStub extends ScStubElement[ScImportSelector] with ScTypeElementOwnerStub[ScImportSelector] {
  def isAliasedImport: Boolean

  def referenceText: Option[String]

  def reference: Option[ScStableCodeReference]

  def isWildcardSelector: Boolean

  def importedName: Option[String]

  def aliasName: Option[String]

  def isGivenSelector: Boolean
}
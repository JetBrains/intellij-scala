package org.jetbrains.plugins.scala.lang.psi.stubs

import api.statements.ScTypeAlias
import com.intellij.psi.stubs.NamedStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

trait ScTypeAliasStub extends NamedStub[ScTypeAlias] {
  def isDeclaration: Boolean
}
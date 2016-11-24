package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScBoundsOwnerStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */
trait ScTypeAliasStub extends ScBoundsOwnerStub[ScTypeAlias] with ScMemberOrLocal {
  def isDeclaration: Boolean

  def typeText: Option[String]

  def typeElement: Option[ScTypeElement]

  def isStableQualifier: Boolean
}

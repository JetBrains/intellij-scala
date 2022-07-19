package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScBoundsOwnerStub, ScTypeElementOwnerStub}

trait ScTypeAliasStub
  extends ScBoundsOwnerStub[ScTypeAlias]
    with ScTopLevelElementStub[ScTypeAlias]
    with ScTypeElementOwnerStub[ScTypeAlias]
    with ScMemberOrLocal[ScTypeAlias] {

  def isDeclaration: Boolean

  def isStableQualifier: Boolean

  def stableQualifier: Option[String]

  def classType: Option[String]
}

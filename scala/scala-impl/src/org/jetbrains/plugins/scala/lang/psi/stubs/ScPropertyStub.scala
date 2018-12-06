package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

/**
  * @author adkozlov
  */
trait ScPropertyStub[P <: ScValueOrVariable] extends StubElement[P]
  with ScTypeElementOwnerStub[P] with ScMemberOrLocal {

  def isDeclaration: Boolean

  def isImplicit: Boolean

  def names: Array[String]

  def bodyText: Option[String]

  def bodyExpression: Option[ScExpression]
}

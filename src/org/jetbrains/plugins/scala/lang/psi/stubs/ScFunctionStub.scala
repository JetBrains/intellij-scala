package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

trait ScFunctionStub extends NamedStub[ScFunction] with ScMemberOrLocal {
  def isImplicit: Boolean

  def isDeclaration: Boolean

  def getAnnotations : Array[String]

  def getReturnTypeText: String

  def getReturnTypeElement: Option[ScTypeElement]

  def getBodyExpression: Option[ScExpression]

  def getBodyText: String

  def hasAssign: Boolean
}
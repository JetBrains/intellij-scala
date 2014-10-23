package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

trait ScParameterStub extends NamedStub[ScParameter] {
  def getTypeElement: Option[ScTypeElement]

  def getTypeText: String

  def isStable: Boolean

  def isDefaultParam: Boolean

  def isRepeated: Boolean

  def isVal: Boolean

  def isVar: Boolean

  def isCallByNameParameter: Boolean

  def getDefaultExprText: Option[String]

  def getDefaultExpr: Option[ScExpression]

  def deprecatedName: Option[String]
}
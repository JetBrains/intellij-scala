package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */

trait ScVariableStub extends StubElement[ScVariable] with ScMemberOrLocal {
  def isDeclaration: Boolean

  def names: Array[String]

  def bodyText: Option[String]

  def typeText: Option[String]

  def bindingsContainerText: Option[String]

  def typeElement: Option[ScTypeElement]

  def bodyExpression: Option[ScExpression]

  def idsContainer: Option[ScIdList]

  def patternsContainer: Option[ScPatternList]
}

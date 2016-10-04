package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

/**
  * @author adkozlov
  */
trait ScValueOrVariableStub[V <: ScValueOrVariable] extends StubElement[V] with ScMemberOrLocal {
  def isDeclaration: Boolean

  def names: Array[String]

  def typeText: Option[String]

  def typeElement: Option[ScTypeElement]

  def bodyText: Option[String]

  def bodyExpression: Option[ScExpression]

  def bindingsContainerText: Option[String]

  def idsContainer: Option[ScIdList]

  def patternsContainer: Option[ScPatternList]
}

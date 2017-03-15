package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIdList, ScPatternList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExpressionOwnerStub, ScTypeElementOwnerStub}

/**
  * @author adkozlov
  */
trait ScValueOrVariableStub[V <: ScValueOrVariable] extends StubElement[V]
  with ScTypeElementOwnerStub[V]  with ScExpressionOwnerStub[V] with ScMemberOrLocal {

  def isDeclaration: Boolean

  def names: Array[String]

  def bindingsContainerText: Option[String]

  def idsContainer: Option[ScIdList]

  def patternsContainer: Option[ScPatternList]
}

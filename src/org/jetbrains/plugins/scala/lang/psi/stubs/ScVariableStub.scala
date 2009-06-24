package org.jetbrains.plugins.scala.lang.psi.stubs


import api.base.types.ScTypeElement
import api.base.{ScPatternList, ScIdList}
import api.expr.ScExpression
import api.statements.{ScValue, ScVariable}
import com.intellij.psi.stubs.{StubElement, NamedStub}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

trait ScVariableStub extends StubElement[ScVariable] {
  def isDeclaration: Boolean
  def getNames: Array[String]
  def getBodyText: String
  def getTypeText: String
  def getBindingsContainerText: String
  def getTypeElement: Option[ScTypeElement]
  def getBodyExpr: Option[ScExpression]
  def getIdsContainer: Option[ScIdList]
  def getPatternsContainer: Option[ScPatternList]
}

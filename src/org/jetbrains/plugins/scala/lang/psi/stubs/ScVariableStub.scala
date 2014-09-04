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
  def getNames: Array[String]
  def getBodyText: String
  def getTypeText: String
  def getBindingsContainerText: String
  def getTypeElement: Option[ScTypeElement]
  def getBodyExpr: Option[ScExpression]
  def getIdsContainer: Option[ScIdList]
  def getPatternsContainer: Option[ScPatternList]
}

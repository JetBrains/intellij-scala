package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import api.base.types.ScTypeElement
import api.base.{ScIdList, ScPatternList}
import api.expr.ScExpression
import api.statements.ScValue
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, NamedStub}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

trait ScValueStub extends StubElement[ScValue] {
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
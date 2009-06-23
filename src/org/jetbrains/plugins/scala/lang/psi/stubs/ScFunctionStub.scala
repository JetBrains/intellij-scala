package org.jetbrains.plugins.scala.lang.psi.stubs

import api.base.types.ScTypeElement
import api.expr.ScExpression
import api.statements.ScFunction
import com.intellij.psi.impl.cache.TypeInfo
import com.intellij.psi.PsiType
import com.intellij.psi.stubs.NamedStub
import types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

trait ScFunctionStub extends NamedStub[ScFunction] {
  def isDeclaration: Boolean

  def getAnnotations : Seq[String]

  def getReturnTypeText: String

  def getReturnTypeElement: Option[ScTypeElement]

  def getBodyExpression: Option[ScExpression]

  def getBodyText: String
}
package org.jetbrains.plugins.scala.lang.psi.stubs

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

  def getReturnType: ScType

  def getBodyText: String
}
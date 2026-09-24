package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement

trait ScTopLevelElementStub[T <: PsiElement] extends ScStubElement[T] {
  def isTopLevel: Boolean
  def topLevelQualifier: Option[String]
}

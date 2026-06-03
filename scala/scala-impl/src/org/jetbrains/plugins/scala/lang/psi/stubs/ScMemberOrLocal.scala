package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement

trait ScMemberOrLocal[T <: PsiElement] extends StubElement[T] {
  def isLocal: Boolean
}

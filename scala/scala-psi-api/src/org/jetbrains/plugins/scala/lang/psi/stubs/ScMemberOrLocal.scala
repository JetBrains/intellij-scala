package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.PsiElement

trait ScMemberOrLocal[T <: PsiElement] extends ScStubElement[T] {
  def isLocal: Boolean
}

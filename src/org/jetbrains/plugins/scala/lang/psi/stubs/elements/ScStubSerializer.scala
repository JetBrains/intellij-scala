package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubSerializer}

/**
  * @author adkozlov
  */
trait ScStubSerializer[S <: StubElement[_ <: PsiElement]] extends StubSerializer[S] {
  protected val debugName: String

  protected val externalIdPrefix: String = "scala"

  def getExternalId = s"$externalIdPrefix.$debugName"
}

package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubSerializer

/**
  * @author adkozlov
  */
trait DottyStubSerializer[S <: StubElement[_ <: PsiElement]] extends ScStubSerializer[S] {
  override protected val externalIdPrefix = "dotty"
}

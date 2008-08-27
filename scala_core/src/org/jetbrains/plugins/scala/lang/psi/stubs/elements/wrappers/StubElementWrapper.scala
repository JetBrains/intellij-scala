package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers

import com.intellij.psi.stubs.StubElement
import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

trait StubElementWrapper[T <: PsiElement] extends StubElement[T] {

}
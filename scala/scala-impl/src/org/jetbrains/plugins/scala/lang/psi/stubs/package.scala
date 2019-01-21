package org.jetbrains.plugins.scala
package lang
package psi

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}

package object stubs {

  private[stubs] type RawStubElement = StubElement[_ <: PsiElement]
  private[stubs] type RawStubElementType = IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement]
}

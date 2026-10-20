package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.stubs._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.stubs.ScStubElement

abstract class ScNamedStubBase[E <: PsiNamedElement] protected[impl](parent: StubElement[? <: PsiElement],
                                                                     elementType: IElementType,
                                                                     @Nullable name: String)
  extends StubBase[E](parent, elementType) with NamedStub[E] with ScStubElement[E] {

  @Nullable
  override final def getName: String = name
}

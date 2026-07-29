package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.stubs._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.annotations.Nullable

abstract class ScNamedStubBase[E <: PsiNamedElement] protected[impl](parent: StubElement[_ <: PsiElement],
                                                                     elementType: IElementType,
                                                                     @Nullable name: String)
  extends StubBase[E](parent, elementType) with NamedStub[E] {

  @Nullable
  override final def getName: String = name
}

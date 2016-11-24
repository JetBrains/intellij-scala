package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.stubs._
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.io.StringRef

/**
  * @author adkozlov
  */
abstract class ScNamedStubBase[E <: PsiNamedElement] protected[impl](parent: StubElement[_ <: PsiElement],
                                                                     elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                                     nameRef: StringRef)
  extends StubBase[E](parent, elementType) with NamedStub[E] {
  override final def getName: String = StringRef.toString(nameRef)
}

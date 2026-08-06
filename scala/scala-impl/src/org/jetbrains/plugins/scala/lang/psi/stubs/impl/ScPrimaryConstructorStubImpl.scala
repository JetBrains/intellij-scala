package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub

class ScPrimaryConstructorStubImpl(parent: StubElement[_ <: PsiElement],
                                   elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBase[ScPrimaryConstructor](parent, elementType) with ScPrimaryConstructorStub
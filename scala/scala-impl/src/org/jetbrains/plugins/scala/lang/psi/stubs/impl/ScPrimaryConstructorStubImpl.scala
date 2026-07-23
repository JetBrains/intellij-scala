package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub

class ScPrimaryConstructorStubImpl(parent: StubElement[_ <: PsiElement],
                                   elementType: IElementType)
  extends StubBase[ScPrimaryConstructor](parent, elementType) with ScPrimaryConstructorStub

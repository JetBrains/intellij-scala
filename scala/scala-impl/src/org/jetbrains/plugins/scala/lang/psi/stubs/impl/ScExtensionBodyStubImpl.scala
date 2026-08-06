package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub

class ScExtensionBodyStubImpl(
  parent:      StubElement[_ <: PsiElement],
  elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement]
) extends StubBase[ScExtensionBody](parent, elementType)
    with ScExtensionBodyStub

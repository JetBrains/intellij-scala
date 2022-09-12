package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub

class ScParamClausesStubImpl(parent: StubElement[_ <: PsiElement],
                             elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBase[ScParameters](parent, elementType) with ScParamClausesStub
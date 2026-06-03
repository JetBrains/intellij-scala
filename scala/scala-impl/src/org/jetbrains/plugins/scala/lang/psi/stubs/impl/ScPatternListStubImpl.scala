package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub

class ScPatternListStubImpl(parent: StubElement[_ <: PsiElement],
                            elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                            override val simplePatterns: Boolean)
  extends StubBase[ScPatternList](parent, elementType) with ScPatternListStub
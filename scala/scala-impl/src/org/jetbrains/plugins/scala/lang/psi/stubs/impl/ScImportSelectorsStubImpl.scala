package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorsStub

class ScImportSelectorsStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                override val hasWildcard: Boolean)
  extends StubBase[ScImportSelectors](parent, elementType) with ScImportSelectorsStub
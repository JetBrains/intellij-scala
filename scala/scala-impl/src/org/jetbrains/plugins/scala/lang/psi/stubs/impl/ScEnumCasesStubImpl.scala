package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub

class ScEnumCasesStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IElementType)
  extends StubBase[ScEnumCases](parent, elementType) with ScEnumCasesStub

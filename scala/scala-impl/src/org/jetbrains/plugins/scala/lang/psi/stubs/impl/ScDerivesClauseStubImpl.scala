package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.stubs.ScDerivesClauseStub

class ScDerivesClauseStubImpl(
  parent:      StubElement[_ <: PsiElement],
  elementType: IElementType
) extends StubBase[ScDerivesClause](parent, elementType)
    with ScDerivesClauseStub

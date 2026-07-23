package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub

class ScTypeParamClauseStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IElementType,
                                override val typeParameterClauseText: String)
  extends StubBase[ScTypeParamClause](parent, elementType) with ScTypeParamClauseStub

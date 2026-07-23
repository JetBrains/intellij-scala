
package org.jetbrains.plugins.scala.lang.psi.stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

class ScParamClauseStubImpl(parent: StubElement[_ <: PsiElement],
                            elementType: IElementType,
                            override val hasImplicitKeyword: Boolean,
                            override val hasUsingKeyword: Boolean,
                           )
  extends StubBase[ScParameterClause](parent, elementType) with ScParamClauseStub

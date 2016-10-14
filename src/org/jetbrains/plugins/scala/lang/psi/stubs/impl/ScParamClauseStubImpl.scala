package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
class ScParamClauseStubImpl(parent: StubElement[_ <: PsiElement],
                            elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                            val isImplicit: Boolean)
  extends StubBase[ScParameterClause](parent, elementType) with ScParamClauseStub
package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.statements.params.ScParameterClause
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParamClauseStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameterClause](parent, elemType) with ScParamClauseStub
package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.statements.params.ScParameters
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParamClausesStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameters](parent, elemType) with ScParamClausesStub
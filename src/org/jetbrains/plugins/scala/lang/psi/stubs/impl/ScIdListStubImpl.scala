package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.{ScIdList}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.07.2009
 */

class ScIdListStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScIdList](parent, elemType) with ScIdListStub
package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.{ScPrimaryConstructor, ScAccessModifier}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScPrimaryConstructorStubImpl [ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScPrimaryConstructor](parent, elemType) with ScPrimaryConstructorStub {

}
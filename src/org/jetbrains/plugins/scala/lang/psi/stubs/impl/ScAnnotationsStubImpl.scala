package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.expr.ScAnnotations
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScAnnotations](parent, elemType) with ScAnnotationsStub {

}
package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.base.ScPatternList
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2009
 */

class ScPatternListStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScPatternList](parent, elemType) with ScPatternListStub
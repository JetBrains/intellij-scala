package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.toplevel.imports.{ScImportSelectors, ScImportSelector}
import elements.ScImportSelectorsStub
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScImportSelectors](parent, elemType) with ScImportSelectorsStub {

}
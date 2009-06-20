package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.statements.params.ScParameters
import api.toplevel.imports.ScImportSelector
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScImportSelector](parent, elemType) with ScImportSelectorStub {

}
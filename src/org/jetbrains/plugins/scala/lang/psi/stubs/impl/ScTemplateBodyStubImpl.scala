package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import api.statements.ScTypeAlias
import api.toplevel.templates.ScTemplateBody
import com.intellij.psi.stubs.{IStubElementType, StubElement}

import com.intellij.psi.PsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTemplateBodyStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
  extends StubBaseWrapper[ScTemplateBody](parent, elemType) with ScTemplateBodyStub {

}
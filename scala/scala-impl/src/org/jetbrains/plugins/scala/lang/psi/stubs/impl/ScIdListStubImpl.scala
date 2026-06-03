package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub

class ScIdListStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBase[ScIdList](parent, elemType) with ScIdListStub
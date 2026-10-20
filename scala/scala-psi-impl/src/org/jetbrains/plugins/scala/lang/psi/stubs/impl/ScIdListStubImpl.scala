package org.jetbrains.plugins.scala.lang.psi.stubs.impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub

class ScIdListStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IElementType)
  extends StubBase[ScIdList](parent, elemType) with ScIdListStub

package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.types.ScSelfTypeElement
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */

class ScSelfTypeElementStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
  extends StubBaseWrapper[ScSelfTypeElement](parent, elemType) with ScSelfTypeElementStub  {

}
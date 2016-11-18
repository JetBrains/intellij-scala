package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScPrimaryConstructorStubImpl(parent: StubElement[_ <: PsiElement],
                                   elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBase[ScPrimaryConstructor](parent, elementType) with ScPrimaryConstructorStub
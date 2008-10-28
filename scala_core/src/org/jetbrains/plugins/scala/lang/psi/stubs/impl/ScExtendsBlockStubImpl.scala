package org.jetbrains.plugins.scala.lang.psi.stubs.impl
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}

/**
 * @author ilyas
 */

class ScExtendsBlockStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScExtendsBlock](parent, elemType) with ScExtendsBlockStub {
  var baseClasses: Array[String] = Array[String]()

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          base: Array[String]) {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    baseClasses = base
  }

  def getBaseClasses: Array[String] = baseClasses
}
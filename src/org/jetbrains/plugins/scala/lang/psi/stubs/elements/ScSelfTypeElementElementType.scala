package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.base.types.ScSelfTypeElement
import impl.ScSelfTypeElementStubImpl
import psi.impl.base.types.ScSelfTypeElementImpl
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */

class ScSelfTypeElementElementType[Func <: ScSelfTypeElement]
        extends ScStubElementType[ScSelfTypeElementStub, ScSelfTypeElement]("self type element") {
  def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = {
    new ScSelfTypeElementImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScSelfTypeElement, parentStub: StubElement[ParentPsi]): ScSelfTypeElementStub = {
    new ScSelfTypeElementStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScSelfTypeElementStub = {
    new ScSelfTypeElementStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {}
}
package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import api.base.{ScPrimaryConstructor, ScModifierList}
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.PsiElement
import impl.ScPrimaryConstructorStubImpl
import psi.impl.base.ScPrimaryConstructorImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScPrimaryConstructorElementType[Func <: ScPrimaryConstructor]
        extends ScStubElementType[ScPrimaryConstructorStub, ScPrimaryConstructor]("primary constructor") {
  def serialize(stub: ScPrimaryConstructorStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScPrimaryConstructorStub): ScPrimaryConstructor = {
    new ScPrimaryConstructorImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScPrimaryConstructor, parentStub: StubElement[ParentPsi]): ScPrimaryConstructorStub = {
    new ScPrimaryConstructorStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPrimaryConstructorStub = {
    new ScPrimaryConstructorStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScPrimaryConstructorStub, sink: IndexSink): Unit = {}
}
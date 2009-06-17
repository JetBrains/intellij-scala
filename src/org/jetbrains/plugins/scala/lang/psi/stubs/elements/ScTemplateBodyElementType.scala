package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import api.toplevel.templates.ScTemplateBody
import psi.impl.toplevel.templates.ScTemplateBodyImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.PsiElement
import impl.ScTemplateBodyStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTemplateBodyElementType[Func <: ScTemplateBody]
        extends ScStubElementType[ScTemplateBodyStub, ScTemplateBody]("template body") {
  def serialize(stub: ScTemplateBodyStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScTemplateBodyStub): ScTemplateBody = {
    new ScTemplateBodyImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTemplateBody, parentStub: StubElement[ParentPsi]): ScTemplateBodyStub = {
    new ScTemplateBodyStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTemplateBodyStub = {
    new ScTemplateBodyStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScTemplateBodyStub, sink: IndexSink): Unit = {}
}
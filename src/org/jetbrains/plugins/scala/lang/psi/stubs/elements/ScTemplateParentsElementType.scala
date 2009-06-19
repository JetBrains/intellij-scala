package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import api.base.ScModifierList
import api.toplevel.templates.ScTemplateParents
import com.intellij.util.io.StringRef
import impl.ScTemplateParentsStubImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

abstract class ScTemplateParentsElementType[Func <: ScTemplateParents](debugName: String)
        extends ScStubElementType[ScTemplateParentsStub, ScTemplateParents](debugName) {
  def serialize(stub: ScTemplateParentsStub, dataStream: StubOutputStream): Unit = {
    val array = stub.getTemplateParentsTypesTexts
    dataStream.writeByte(array.length)
    for (s <- array) {
      dataStream.writeName(s)
    }
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTemplateParents, parentStub: StubElement[ParentPsi]): ScTemplateParentsStub = {
    new ScTemplateParentsStubImpl(parentStub, this, psi.typeElements.map(_.getText).toArray)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTemplateParentsStub = {
    val l = dataStream.readByte
    val res = new Array[String](l)
    for (i <- 1 to l) {
      res(i - 1) = StringRef.toString(dataStream.readName)
    }
    new ScTemplateParentsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, res)
  }

  def indexStub(stub: ScTemplateParentsStub, sink: IndexSink): Unit = {}
}
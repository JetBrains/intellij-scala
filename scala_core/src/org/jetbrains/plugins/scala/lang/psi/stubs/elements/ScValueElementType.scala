package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.{ScValue, ScValueDeclaration}

import api.toplevel.templates.ScTemplateBody
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScValueStubImpl
import index.ScalaIndexKeys
import java.io.IOException

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

abstract class ScValueElementType[Value <: ScValue](debugName: String)
extends ScStubElementType[ScValueStub, ScValue](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScValue, parentStub: StubElement[ParentPsi]): ScValueStub = {
    new ScValueStubImpl[ParentPsi](parentStub, this, (for (elem <- psi.declaredElements) yield elem.getName).toArray, psi.isInstanceOf[ScValueDeclaration])
  }

  def serialize(stub: ScValueStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeByte(names.length)
    for (name <- names) dataStream.writeName(name)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScValueStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readByte
    val names = new Array[String](namesLength)
    for (i <- 0 to (namesLength - 1)) names(i) = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScValueStubImpl(parent, this, names, isDecl)
  }

  def indexStub(stub: ScValueStub, sink: IndexSink): Unit = {
    val names = stub.getNames
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VALUE_NAME_KEY, name)
    }
  }
}